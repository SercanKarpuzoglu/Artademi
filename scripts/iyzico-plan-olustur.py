#!/usr/bin/env python3
"""
iyzico abonelik urunu + AYLIK plani olusturur ve IYZICO_PLAN_REF degerini yazdirir.

NEDEN BETIK: canlı gizli anahtar (secret key) hicbir yere kopyalanmadan, yalnizca bu makinede
kullanilir. Anahtarlar ortam degiskeninden okunur — koda/repoya GOMULMEZ.

Kullanim (canli):
    export IYZICO_BASE_URL=https://api.iyzipay.com
    export IYZICO_API_KEY='...'          # canli panel > Ayarlar > API anahtarlari
    export IYZICO_SECRET_KEY='...'
    python3 scripts/iyzico-plan-olustur.py

Sandbox icin ayni betik: IYZICO_BASE_URL=https://sandbox-api.iyzipay.com

Cikti: olusan planin referans kodu → .env.prod'daki IYZICO_PLAN_REF'e yazilir.

⚠️  MEVCUT ABONELER ESKI FIYATTA KALIR. iyzico'da bir planin fiyati sonradan
degistirilmez; yeni fiyat icin YENI PLAN acilir ve yalnizca yeni abonelikler ona baglanir.
Eski abonelerin yeni fiyata gecmesi ancak abonelik iptali + yeniden kayit ile olur.
"""

import base64
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE_URL = os.environ.get("IYZICO_BASE_URL", "https://api.iyzipay.com").rstrip("/")
API_KEY = os.environ.get("IYZICO_API_KEY", "")
SECRET_KEY = os.environ.get("IYZICO_SECRET_KEY", "")

URUN_ADI = os.environ.get("IYZICO_URUN_ADI", "Artademi Tam Paket")
PARA_BIRIMI = "TRY"

# KDV haric, landing'de ilan edilen tutarlar. Plan adina fiyati da yaziyoruz:
# iyzico panelinde hangi planin hangi fiyat oldugu boylece tek bakista gorunur ve
# ayni isimde ikinci plan acilmaya calisildiginda cakisma yasanmaz.
AYLIK_FIYAT = os.environ.get("BILLING_AYLIK_UCRET", "2000")


def cagir(method, path, body=None):
    """IYZWSv2 imzali istek (docs.iyzico.com): hex(HMAC-SHA256(rnd + path + body, secret))."""
    payload = json.dumps(body) if body is not None else ""
    rnd = str(int(time.time() * 1000)) + uuid.uuid4().hex[:8]
    # ⚠️ Imza QUERY STRING'I ICERMEZ (canli API'de olculdu): query dahil edilirse iyzico
    # "Authentication token is not verified" (errorCode 8) doner.
    imza_yolu = path.split("?")[0]
    imza = hmac.new(SECRET_KEY.encode(), (rnd + imza_yolu + payload).encode(),
                    hashlib.sha256).hexdigest()
    yetki = base64.b64encode(
        f"apiKey:{API_KEY}&randomKey:{rnd}&signature:{imza}".encode()).decode()

    istek = urllib.request.Request(
        BASE_URL + path,
        data=payload.encode() if payload else None,
        method=method,
    )
    istek.add_header("Authorization", "IYZWSv2 " + yetki)
    istek.add_header("x-iyzi-rnd", rnd)
    istek.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(istek) as cevap:
            return json.load(cevap)
    except urllib.error.HTTPError as e:
        return json.load(e)


def urun_bul(ad):
    """Ayni isimli urun zaten varsa referansini doner (canlida urun bir kez acilir)."""
    cevap = cagir("GET", "/v2/subscription/products")
    if cevap.get("status") != "success":
        return None
    for urun in (cevap.get("data") or {}).get("items") or []:
        if urun.get("name") == ad:
            return urun.get("referenceCode")
    return None


def plan_olustur(urun_ref, ad, fiyat, aralik):
    """Tek bir fiyat plani acar; basarisizsa None doner (sebep ekrana yazilir)."""
    plan = cagir("POST", f"/v2/subscription/products/{urun_ref}/pricing-plans", {
        "locale": "tr",
        "name": ad,
        "price": fiyat,
        "currencyCode": PARA_BIRIMI,
        "paymentInterval": aralik,
        "paymentIntervalCount": 1,
        "trialPeriodDays": 0,
        "planPaymentType": "RECURRING",
    })
    if plan.get("status") != "success":
        print("   BASARISIZ:", plan.get("errorMessage"), plan.get("errorCode", ""))
        if plan.get("errorCode") == "201001":
            print("   ℹ️  Bu isimde plan ZATEN VAR — referansini iyzico panelinden alin.")
        return None
    return plan["data"]["referenceCode"]


def main() -> int:
    if not API_KEY or not SECRET_KEY:
        print("HATA: IYZICO_API_KEY ve IYZICO_SECRET_KEY ortam degiskenleri gerekli.")
        return 1

    ortam = "CANLI" if "sandbox" not in BASE_URL else "SANDBOX"
    print(f"Ortam: {ortam} ({BASE_URL})\n")

    print("1) Urun aranıyor…")
    urun_ref = urun_bul(URUN_ADI)
    if urun_ref:
        print(f"   Mevcut urun kullanilacak: {urun_ref}")
    else:
        print("   Bulunamadi, olusturuluyor…")
        urun = cagir("POST", "/v2/subscription/products", {
            "locale": "tr",
            "name": URUN_ADI,
            "description": "Sanat akademileri icin yonetim platformu",
        })
        if urun.get("status") != "success":
            print("   BASARISIZ:", urun.get("errorMessage"), urun.get("errorCode", ""))
            if urun.get("errorCode") == "100001":
                print("\n   ⚠️  'Sistem hatasi' = Abonelik modulu bu hesapta AKTIF DEGIL.")
                print("   entegrasyon@iyzico.com adresine uye isyeri numaraniz ile yazip")
                print("   Subscription API'nin acilmasini isteyin.")
            return 1
        urun_ref = urun["data"]["referenceCode"]
        print(f"   OK — urun referansi: {urun_ref}")

    print(f"2) Aylik plan olusturuluyor ({AYLIK_FIYAT} {PARA_BIRIMI})…")
    aylik_ref = plan_olustur(urun_ref, f"Aylik Tam Paket {AYLIK_FIYAT}", AYLIK_FIYAT, "MONTHLY")
    if not aylik_ref:
        return 1

    print()
    print("=" * 60)
    print(f"IYZICO_PLAN_REF={aylik_ref}")
    print("=" * 60)
    print("\nBu satiri sunucudaki .env.prod dosyasina yazin, sonra backend'i yeniden baslatin.")
    print("NOT: Mevcut aboneler ESKI fiyat planinda kalir; yeni fiyat yalnizca yeni")
    print("     aboneliklere uygulanir.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
