#!/usr/bin/env bash
#
# verify-roles.sh — Rol/yetki matrisini Keycloak tokenlariyla CANLI dogrular.
#
# Her rolun token'ini alir ve secili uclarda beklenen HTTP kodunu kontrol eder;
# tek komutta PASS/FAIL tablosu yazar. Boylece yetki davranisini elle curl atmadan
# tekrar tekrar dogrulayabiliriz: ./scripts/verify-roles.sh
#
# Beklentiler (onceki modullerle tutarli):
#   - finance (tahsilat/muhasebe)         -> ADMIN + FRONTDESK_ACCOUNTING; FRONTDESK/TEACHER 403
#   - payout/report-financial (maas/hakedis) -> yalnizca ADMIN; digerleri 403
#   - report group-occupancy (operasyonel)  -> ADMIN + FRONTDESK_ACCOUNTING + FRONTDESK; TEACHER 403
#   - /api/students                          -> ADMIN + FRONTDESK*; TEACHER 403
#   - TEACHER kendi grubu (grup 1)           -> 200 (yoklama koprusu)
#
# Gereksinimler: calisir backend (BASE) + Keycloak (KC), curl, python3.
# NOT: Parola sir DEGIL diye gomulmez. Lokal test icin varsayilan Test1234! kabul;
#      gercek ortamda PASSWORD degiskeniyle override edin:  PASSWORD=*** ./scripts/verify-roles.sh
set -u

KC="${KC:-http://localhost:8080}"
BASE="${BASE:-http://localhost:8081}"
REALM="${REALM:-Artademi}"
CLIENT="${CLIENT:-artademi-app}"
# Lokal test parolasi; CI/gercek ortamda PASSWORD env ile gecin.
PASSWORD="${PASSWORD:-Test1234!}"

# Rol -> kullanici adi
USER_ADMIN="${USER_ADMIN:-admin.a}"
USER_ACCOUNTING="${USER_ACCOUNTING:-accounting.a}"   # FRONTDESK_ACCOUNTING
USER_FRONTDESK="${USER_FRONTDESK:-frontdesk.a}"      # FRONTDESK
USER_TEACHER="${USER_TEACHER:-teacher.a}"            # TEACHER (kendi grubu: grup 1)

pass=0
fail=0

# token <username> -> stdout'a access_token (bos ise login basarisiz)
token() {
  curl -s -X POST "$KC/realms/$REALM/protocol/openid-connect/token" \
    -d grant_type=password -d "client_id=$CLIENT" \
    -d "username=$1" -d "password=$PASSWORD" \
    | python3 -c "import sys,json;print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null
}

# check <etiket> <token> <method> <path> <beklenen_kod> [json_body]
check() {
  local label="$1" tok="$2" method="$3" path="$4" want="$5" body="${6:-}"
  local code
  if [ -n "$body" ]; then
    code=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $tok" -H "Content-Type: application/json" -d "$body")
  else
    code=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$BASE$path" \
      -H "Authorization: Bearer $tok")
  fi
  if [ "$code" = "$want" ]; then
    printf "  PASS  %-42s beklenen=%s aldi=%s\n" "$label" "$want" "$code"
    pass=$((pass+1))
  else
    printf "  FAIL  %-42s beklenen=%s aldi=%s\n" "$label" "$want" "$code"
    fail=$((fail+1))
  fi
}

echo "== Token aliniyor (realm=$REALM, client=$CLIENT) =="
T_ADMIN=$(token "$USER_ADMIN")
T_ACC=$(token "$USER_ACCOUNTING")
T_FD=$(token "$USER_FRONTDESK")
T_TEA=$(token "$USER_TEACHER")
for pair in "ADMIN:$T_ADMIN" "ACCOUNTING:$T_ACC" "FRONTDESK:$T_FD" "TEACHER:$T_TEA"; do
  name="${pair%%:*}"; val="${pair#*:}"
  if [ -z "$val" ]; then echo "  HATA: $name token alinamadi (kullanici/parola?)"; exit 2; fi
done
echo "  tum tokenlar alindi."

echo
echo "== 1) finance: tahsilat listesi (ADMIN+ACCOUNTING 200; FRONTDESK+TEACHER 403) =="
check "ADMIN      GET /api/payments"      "$T_ADMIN" GET /api/payments 200
check "ACCOUNTING GET /api/payments"      "$T_ACC"   GET /api/payments 200
check "FRONTDESK  GET /api/payments"      "$T_FD"    GET /api/payments 403
check "TEACHER    GET /api/payments"      "$T_TEA"   GET /api/payments 403

echo
echo "== 2) payout (maas): yalnizca ADMIN 200; digerleri 403 =="
check "ADMIN      GET /api/payouts"       "$T_ADMIN" GET /api/payouts 200
check "ACCOUNTING GET /api/payouts"       "$T_ACC"   GET /api/payouts 403
check "FRONTDESK  GET /api/payouts"       "$T_FD"    GET /api/payouts 403
check "TEACHER    GET /api/payouts"       "$T_TEA"   GET /api/payouts 403

echo
echo "== 3) report financial-summary (maas/hakedis icerir): yalnizca ADMIN =="
check "ADMIN      GET financial-summary"  "$T_ADMIN" GET "/api/reports/financial-summary?donem=2026-06" 200
check "ACCOUNTING GET financial-summary"  "$T_ACC"   GET "/api/reports/financial-summary?donem=2026-06" 403
check "TEACHER    GET financial-summary"  "$T_TEA"   GET "/api/reports/financial-summary?donem=2026-06" 403

echo
echo "== 4) report group-occupancy (operasyonel): ADMIN+ACCOUNTING+FRONTDESK 200; TEACHER 403 =="
check "ADMIN      GET group-occupancy"    "$T_ADMIN" GET /api/reports/group-occupancy 200
check "ACCOUNTING GET group-occupancy"    "$T_ACC"   GET /api/reports/group-occupancy 200
check "FRONTDESK  GET group-occupancy"    "$T_FD"    GET /api/reports/group-occupancy 200
check "TEACHER    GET group-occupancy"    "$T_TEA"   GET /api/reports/group-occupancy 403

echo
echo "== 5) /api/students: ADMIN+FRONTDESK* 200; TEACHER 403 =="
check "ADMIN      GET /api/students"      "$T_ADMIN" GET /api/students 200
check "ACCOUNTING GET /api/students"      "$T_ACC"   GET /api/students 200
check "FRONTDESK  GET /api/students"      "$T_FD"    GET /api/students 200
check "TEACHER    GET /api/students"      "$T_TEA"   GET /api/students 403

echo
echo "== 6) Yoklama koprusu: TEACHER (Selin) kendi grubu (grup 1) -> 200 =="
check "TEACHER    GET grup1 attendance"   "$T_TEA"   GET /api/groups/1/attendance-sessions 200

echo
echo "==================== SONUC ===================="
echo "  PASS=$pass  FAIL=$fail"
if [ "$fail" -ne 0 ]; then echo "  DURUM: FAIL"; exit 1; fi
echo "  DURUM: TUMU PASS"
