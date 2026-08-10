import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useSearchParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type {
  BillingSubscriptionResponse,
  SubscriptionPaymentStatus,
  SubscriptionStatus,
} from '../../api/types';
import IyzicoCheckoutForm from './IyzicoCheckoutForm';
import {
  billingSchema,
  toCheckoutPayload,
  type BillingFormValues,
} from './billingSchema';
import {
  useBillingSubscription,
  useCancelSubscription,
  useInvalidateBilling,
  useStartCheckout,
} from './useBilling';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

const STATUS_LABEL: Record<SubscriptionStatus, string> = {
  DENEME: 'Deneme',
  AKTIF: 'Aktif',
  ODEME_BEKLIYOR: 'Ödeme Bekliyor',
  ASKIDA: 'Askıda',
  IPTAL: 'İptal',
};

const STATUS_BADGE: Record<SubscriptionStatus, string> = {
  DENEME: 'badge b-blue',
  AKTIF: 'badge b-green',
  ODEME_BEKLIYOR: 'badge b-amber',
  ASKIDA: 'badge b-red',
  IPTAL: 'badge b-red',
};

const PAYMENT_LABEL: Record<SubscriptionPaymentStatus, string> = {
  BEKLIYOR: 'Bekliyor',
  ODENDI: 'Ödendi',
  BASARISIZ: 'Başarısız',
};

function tarih(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('tr-TR', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  });
}

/**
 * Abonelik ve ödeme sayfası (SADECE ADMIN — route guard + backend 403).
 * iyzico callback'i buraya ?sonuc=basarili|hata ile döner; sonuç banner'ı gösterilir ve
 * abonelik özeti tazelenir. Kart bilgisi iyzico'nun iframe'inde alınır (bkz. IyzicoCheckoutForm).
 */
export default function AbonelikPage() {
  const subQuery = useBillingSubscription();
  const invalidate = useInvalidateBilling();
  const [params, setParams] = useSearchParams();
  const [sonuc, setSonuc] = useState<'basarili' | 'hata' | null>(null);

  // Callback dönüşü: parametreyi bir kez okuyup URL'den temizle, özeti tazele.
  useEffect(() => {
    const s = params.get('sonuc');
    if (s === 'basarili' || s === 'hata') {
      setSonuc(s);
      params.delete('sonuc');
      setParams(params, { replace: true });
      if (s === 'basarili') invalidate();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (subQuery.isLoading) {
    return <div className="card text-center text-ink-soft">Yükleniyor…</div>;
  }
  if (subQuery.isError || !subQuery.data) {
    return (
      <div className="card text-center text-red">
        {subQuery.error instanceof ApiException
          ? subQuery.error.message
          : 'Abonelik bilgisi yüklenemedi'}
      </div>
    );
  }

  const data = subQuery.data;

  return (
    <div className="mx-auto max-w-3xl">
      <div className="topbar">
        <div>
          <h1>Abonelik</h1>
          <div className="sub">Artademi aboneliğiniz ve otomatik ödeme</div>
        </div>
      </div>

      <div className="space-y-4">
        {sonuc === 'basarili' && (
          <div className="card border-green/40 bg-green/10 text-green">
            Ödemeniz alındı, otomatik ödeme başlatıldı. Teşekkürler! Aylık tahsilatlar bundan
            sonra kayıtlı kartınızdan otomatik yapılır.
          </div>
        )}
        {sonuc === 'hata' && (
          <div className="card border-red/40 bg-red/10 text-red">
            Ödeme tamamlanamadı. Kart bilgilerinizi kontrol edip yeniden deneyebilirsiniz; sorun
            sürerse info@artademi.com adresinden bize ulaşın.
          </div>
        )}

        <SubscriptionCard data={data} />

        {!data.otomatikOdemeAktif && !data.subscription.cancelAtPeriodEnd && <CheckoutCard />}

        {data.otomatikOdemeAktif && !data.subscription.cancelAtPeriodEnd && <IptalKarti />}
      </div>
    </div>
  );
}

function SubscriptionCard({ data }: { data: BillingSubscriptionResponse }) {
  const s = data.subscription;
  const graceAktif = s.status === 'ODEME_BEKLIYOR' && s.graceEndsAt;

  return (
    <div className="card">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-[15px] font-semibold">Abonelik Durumu</h2>
        <span className={STATUS_BADGE[s.status]}>{STATUS_LABEL[s.status]}</span>
      </div>

      <dl className="grid grid-cols-1 gap-x-8 gap-y-2 text-[13.5px] sm:grid-cols-2">
        <div className="flex justify-between gap-4">
          <dt className="text-ink-soft">Plan</dt>
          <dd>{s.plan === 'AYLIK' ? 'Aylık' : 'Deneme'}</dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt className="text-ink-soft">Ödeme durumu</dt>
          <dd>{PAYMENT_LABEL[s.paymentStatus]}</dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt className="text-ink-soft">Dönem başlangıcı</dt>
          <dd>{tarih(s.currentPeriodStart)}</dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt className="text-ink-soft">Dönem bitişi</dt>
          <dd>{tarih(s.currentPeriodEnd)}</dd>
        </div>
        <div className="flex justify-between gap-4">
          <dt className="text-ink-soft">Otomatik ödeme</dt>
          <dd>
            {data.otomatikOdemeAktif ? (
              <span className="badge b-green">Aktif · {data.provider ?? 'iyzico'}</span>
            ) : (
              <span className="badge b-gray">Kapalı</span>
            )}
          </dd>
        </div>
      </dl>

      {graceAktif && (
        <div className="mt-4 rounded-[10px] border border-amber/40 bg-amber/10 px-3 py-2 text-[13px] text-amber">
          Ödemeniz gecikti. <b>{tarih(s.graceEndsAt)}</b> tarihine kadar erişiminiz açık kalır;
          bu tarihe kadar ödeme alınamazsa hesabınız askıya alınır.
        </div>
      )}
      {s.status === 'ASKIDA' && (
        <div className="mt-4 rounded-[10px] border border-red/40 bg-red/10 px-3 py-2 text-[13px] text-red">
          Hesabınız askıda — ekipleriniz uygulamaya erişemiyor. Aşağıdan ödemeyi tamamladığınızda
          erişim otomatik olarak açılır.
        </div>
      )}
      {s.cancelAtPeriodEnd && (
        <div className="mt-4 rounded-[10px] border border-amber/40 bg-amber/10 px-3 py-2 text-[13px] text-amber">
          Aboneliğiniz iptal edildi. <b>{tarih(s.currentPeriodEnd)}</b> tarihine kadar
          erişiminiz açık kalır; bu tarihten sonra yenileme yapılmaz ve kartınızdan çekim olmaz.
          Devam etmek isterseniz info@artademi.com ile iletişime geçin.
        </div>
      )}
      {s.muafMi && (
        <div className="mt-4 rounded-[10px] border border-line px-3 py-2 text-[13px] text-ink-soft">
          Bu hesap Artademi tarafından ödemeden muaf tutulmuştur.
        </div>
      )}
    </div>
  );
}

/**
 * Abonelik iptali. İki aşamalı onay: yıkıcı ve parayla ilgili bir işlem olduğu için
 * tek tıkla gerçekleşmez. İptalin ANINDA kesinti YAPMADIĞI açıkça yazılır — kullanıcı
 * "şimdi mi kapanacak?" endişesiyle vazgeçmesin.
 */
function IptalKarti() {
  const [onayAcik, setOnayAcik] = useState(false);
  const [hata, setHata] = useState<string | null>(null);
  const iptal = useCancelSubscription();
  const invalidate = useInvalidateBilling();

  const onIptal = async () => {
    setHata(null);
    try {
      await iptal.mutateAsync();
      invalidate();
      setOnayAcik(false);
    } catch (e) {
      setHata(e instanceof ApiException ? e.message : 'İptal işlemi tamamlanamadı');
    }
  };

  return (
    <div className="card">
      <h2 className="mb-1 text-[15px] font-semibold">Aboneliği İptal Et</h2>
      <p className="mb-3 text-[13px] text-ink-soft">
        Aboneliğinizi dilediğiniz zaman iptal edebilirsiniz. İptal ettiğinizde{' '}
        <b>ödediğiniz dönemin sonuna kadar</b> erişiminiz sürer; sonrasında yenileme yapılmaz ve
        kartınızdan çekim olmaz. <b>Verileriniz silinmez.</b>
      </p>

      {hata && (
        <div className="mb-3 rounded-[10px] border border-red/40 bg-red/10 px-3 py-2 text-[13px] text-red">
          {hata}
        </div>
      )}

      {!onayAcik ? (
        <button type="button" className="btn btn-ghost" onClick={() => setOnayAcik(true)}>
          Aboneliği iptal et
        </button>
      ) : (
        <div className="rounded-[10px] border border-amber/40 bg-amber/10 p-3">
          <p className="mb-3 text-[13px]">
            Aboneliğinizi iptal etmek istediğinize emin misiniz? Bu işlem otomatik yenilemeyi
            durdurur.
          </p>
          <div className="flex gap-2">
            <button
              type="button"
              className="btn btn-primary"
              disabled={iptal.isPending}
              onClick={onIptal}
            >
              {iptal.isPending ? 'İptal ediliyor…' : 'Evet, iptal et'}
            </button>
            <button type="button" className="btn btn-ghost" onClick={() => setOnayAcik(false)}>
              Vazgeç
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function CheckoutCard() {
  const checkout = useStartCheckout();
  const [formContent, setFormContent] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<BillingFormValues>({ resolver: zodResolver(billingSchema) });

  const onSubmit = handleSubmit(async (values) => {
    setApiError(null);
    try {
      const session = await checkout.mutateAsync(toCheckoutPayload(values));
      setFormContent(session.checkoutFormContent);
    } catch (e) {
      setApiError(e instanceof ApiException ? e.message : 'Ödeme başlatılamadı');
    }
  });

  // iyzico formu geldiyse fatura formu yerine onu göster (kart bilgisi iyzico iframe'inde).
  if (formContent) {
    return (
      <div className="card">
        <h2 className="mb-3 text-[15px] font-semibold">Kart Bilgileri</h2>
        <p className="mb-3 text-[13px] text-ink-soft">
          Ödeme formu iyzico güvencesiyle açılır; kart bilgileriniz Artademi sunucularına
          ulaşmaz.
        </p>
        <IyzicoCheckoutForm content={formContent} />
      </div>
    );
  }

  return (
    <div className="card">
      <h2 className="mb-1 text-[15px] font-semibold">Otomatik Ödemeyi Başlat</h2>
      <p className="mb-4 text-[13px] text-ink-soft">
        Fatura bilgilerinizi doldurun; ardından iyzico güvenli ödeme formunda kartınızı
        kaydedin. Aylık abonelik bedeli her dönem otomatik tahsil edilir.
      </p>

      {apiError && (
        <div className="mb-3 rounded-[10px] border border-red/40 bg-red/10 px-3 py-2 text-[13px] text-red">
          {apiError}
        </div>
      )}

      <form onSubmit={onSubmit} className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Field label="Ad" error={errors.ad?.message}>
          <input className={inputClass} {...register('ad')} />
        </Field>
        <Field label="Soyad" error={errors.soyad?.message}>
          <input className={inputClass} {...register('soyad')} />
        </Field>
        <Field label="E-posta" error={errors.email?.message}>
          <input className={inputClass} type="email" {...register('email')} />
        </Field>
        <Field label="Telefon" error={errors.telefon?.message}>
          <input className={inputClass} placeholder="0555 111 22 33" {...register('telefon')} />
        </Field>
        <Field label="TCKN / VKN" error={errors.kimlikVergiNo?.message}>
          <input className={inputClass} inputMode="numeric" {...register('kimlikVergiNo')} />
        </Field>
        <Field label="Şehir" error={errors.sehir?.message}>
          <input className={inputClass} {...register('sehir')} />
        </Field>
        <div className="sm:col-span-2">
          <Field label="Fatura adresi" error={errors.adres?.message}>
            <input className={inputClass} {...register('adres')} />
          </Field>
        </div>
        <div className="sm:col-span-2">
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
            {isSubmitting ? 'Hazırlanıyor…' : 'Ödemeye Geç'}
          </button>
        </div>
      </form>
    </div>
  );
}

function Field({
  label,
  error,
  children,
}: {
  label: string;
  error?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="block text-[13px]">
      <span className="mb-1 block text-ink-soft">{label}</span>
      {children}
      {error && <span className="mt-1 block text-[12px] text-red">{error}</span>}
    </label>
  );
}
