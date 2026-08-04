#!/usr/bin/env python3
"""
iyzico abonelik urunu + aylik plani olusturur ve IYZICO_PLAN_REF degerini yazdirir.

NEDEN BETIK: canlı gizli anahtar (secret key) hicbir yere kopyalanmadan, yalnizca bu makinede
kullanilir. Anahtarlar ortam degiskeninden okunur — koda/repoya GOMULMEZ.

Kullanim (canli):
    export IYZICO_BASE_URL=https://api.iyzipay.com
    export IYZICO_API_KEY='...'          # canli panel > Ayarlar > API anahtarlari
    export IYZICO_SECRET_KEY='...'
    python3 scripts/iyzico-plan-olustur.py

Sandbox icin ayni betik: IYZICO_BASE_URL=https://sandbox-api.iyzipay.com

Cikti: olusan planin referans kodu → .env.prod'daki IYZICO_PLAN_REF'e yazilir.
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

URUN_ADI = "Artademi Tam Paket"
PLAN_ADI = "Aylik Tam Paket"
PLAN_FIYAT = "10000"          # KDV haric, landing'de ilan edilen tutar
PLAN_PARA_BIRIMI = "TRY"


def cagir(method, path, body=None):
    """IYZWSv2 imzali istek (docs.iyzico.com): hex(HMAC-SHA256(rnd + path + body, secret))."""
    payload = json.dumps(body) if body is not None else ""
    rnd = str(int(time.time() * 1000)) + uuid.uuid4().hex[:8]
    imza = hmac.new(SECRET_KEY.encode(), (rnd + path + payload).encode(),
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


def main() -> int:
    if not API_KEY or not SECRET_KEY:
        print("HATA: IYZICO_API_KEY ve IYZICO_SECRET_KEY ortam degiskenleri gerekli.")
        return 1

    ortam = "CANLI" if "sandbox" not in BASE_URL else "SANDBOX"
    print(f"Ortam: {ortam} ({BASE_URL})\n")

    print("1) Urun olusturuluyor…")
    urun = cagir("POST", "/v2/subscription/products", {
        "locale": "tr",
        "name": URUN_ADI,
        "description": "Sanat akademileri icin yonetim platformu - aylik abonelik",
    })
    if urun.get("status") != "success":
        print("   BASARISIZ:", urun.get("errorMessage"), urun.get("errorCode", ""))
        if urun.get("errorCode") == "100001":
            print("\n   ⚠️  'Sistem hatasi' = Abonelik modulu bu hesapta AKTIF DEGIL.")
            print("   entegrasyon@iyzico.com adresine uye isyeri numaraniz ile yazip")
            print("   Subscription API'nin acilmasini isteyin (sandbox'ta da boyle olmustu).")
        if urun.get("errorCode") == "201001":
            print("\n   ℹ️  Bu isimde urun ZATEN VAR — plan da kurulmus olabilir.")
            print("   Mevcut planin referansini almak icin iyzico panelinden bakin ya da")
            print("   URUN_ADI degerini degistirip betigi tekrar calistirin.")
        return 1
    urun_ref = urun["data"]["referenceCode"]
    print(f"   OK — urun referansi: {urun_ref}")

    print("2) Aylik plan olusturuluyor…")
    plan = cagir("POST", f"/v2/subscription/products/{urun_ref}/pricing-plans", {
        "locale": "tr",
        "name": PLAN_ADI,
        "price": PLAN_FIYAT,
        "currencyCode": PLAN_PARA_BIRIMI,
        "paymentInterval": "MONTHLY",
        "paymentIntervalCount": 1,
        "trialPeriodDays": 0,
        "planPaymentType": "RECURRING",
    })
    if plan.get("status") != "success":
        print("   BASARISIZ:", plan.get("errorMessage"), plan.get("errorCode", ""))
        return 1
    plan_ref = plan["data"]["referenceCode"]

    print(f"   OK — plan: {PLAN_FIYAT} {PLAN_PARA_BIRIMI}/ay\n")
    print("=" * 60)
    print(f"IYZICO_PLAN_REF={plan_ref}")
    print("=" * 60)
    print("\nBu satiri sunucudaki .env.prod dosyasina ekleyin.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
