import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiException } from '../../api/client';
import { sendFeedback, type FeedbackTipi } from '../../api/feedback';
import { useMe } from '../../auth/useMe';

const TIPLER: ReadonlyArray<{ key: FeedbackTipi; label: string; aciklama: string }> = [
  { key: 'HATA', label: 'Hata bildir', aciklama: 'Bir şey beklediğim gibi çalışmıyor' },
  { key: 'ONERI', label: 'Öneri', aciklama: 'Şu özellik olsa iyi olurdu' },
  { key: 'DESTEK', label: 'Destek', aciklama: 'Nasıl yapacağımı bilmiyorum' },
  { key: 'DIGER', label: 'Diğer', aciklama: 'Söylemek istediğim başka bir şey' },
];

const schema = z.object({
  tip: z.enum(['HATA', 'ONERI', 'DESTEK', 'DIGER']),
  mesaj: z
    .string()
    .trim()
    .min(10, 'Lütfen en az 10 karakter yazın — ne olduğunu anlayabilelim')
    .max(4000, 'En fazla 4000 karakter'),
  eposta: z
    .string()
    .trim()
    .optional()
    .refine((v) => !v || z.string().email().safeParse(v).success, 'Geçerli bir e-posta girin'),
});

type FormValues = z.infer<typeof schema>;

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

/**
 * Geri bildirim / destek sayfası (giriş yapmış HER rol — öğretmen de sorun bildirebilmeli).
 * Kimlik oturumdan alınır; kullanıcıya ad/kurum sorulmaz. Mesaj info@artademi.com'a düşer.
 */
export default function GeriBildirimPage() {
  const meQuery = useMe();
  const [gonderildi, setGonderildi] = useState(false);
  const [apiHata, setApiHata] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { tip: 'DESTEK', mesaj: '', eposta: meQuery.data?.email ?? '' },
  });

  const mutation = useMutation({ mutationFn: sendFeedback });
  const seciliTip = watch('tip');

  const onSubmit = handleSubmit(async (values) => {
    setApiHata(null);
    try {
      await mutation.mutateAsync({
        tip: values.tip,
        mesaj: values.mesaj,
        eposta: values.eposta?.trim() || undefined,
      });
      setGonderildi(true);
      reset({ tip: 'DESTEK', mesaj: '', eposta: values.eposta });
    } catch (e) {
      setApiHata(e instanceof ApiException ? e.message : 'Gönderilemedi, lütfen tekrar deneyin');
    }
  });

  return (
    <div className="mx-auto max-w-2xl">
      <div className="topbar">
        <div>
          <h1>Geri Bildirim</h1>
          <div className="sub">Sorununuzu, önerinizi veya talebinizi bize iletin</div>
        </div>
      </div>

      {gonderildi && (
        <div className="card mb-4 border-green/40 bg-green/10 text-[13.5px] text-green">
          Mesajınız bize ulaştı, teşekkürler. En kısa sürede dönüş yapacağız.
        </div>
      )}

      <form className="card space-y-4" onSubmit={onSubmit}>
        <div>
          <span className="mb-2 block text-[13px] text-ink-soft">Konu</span>
          <div className="grid gap-2 sm:grid-cols-2">
            {TIPLER.map((t) => (
              <button
                key={t.key}
                type="button"
                onClick={() => setValue('tip', t.key)}
                className={`rounded-[10px] border px-3 py-2 text-left transition-colors ${
                  seciliTip === t.key ? 'border-rasp bg-rasp/5' : 'border-line hover:border-ink-soft'
                }`}
              >
                <span className="block text-[13.5px] font-semibold">{t.label}</span>
                <span className="block text-[12px] text-ink-soft">{t.aciklama}</span>
              </button>
            ))}
          </div>
        </div>

        <label className="block">
          <span className="mb-1 block text-[13px] text-ink-soft">Mesajınız</span>
          <textarea
            className={`${inputClass} min-h-[140px]`}
            placeholder="Olabildiğince somut anlatın: hangi ekrandaydınız, ne yaptınız, ne olmasını bekliyordunuz?"
            {...register('mesaj')}
          />
          {errors.mesaj && (
            <span className="mt-1 block text-[12px] text-red">{errors.mesaj.message}</span>
          )}
        </label>

        <label className="block">
          <span className="mb-1 block text-[13px] text-ink-soft">
            Dönüş için e-posta (opsiyonel)
          </span>
          <input className={inputClass} placeholder="ornek@kurum.com" {...register('eposta')} />
          {errors.eposta && (
            <span className="mt-1 block text-[12px] text-red">{errors.eposta.message}</span>
          )}
        </label>

        {apiHata && (
          <div className="rounded-[10px] border border-red/40 bg-red/10 px-3 py-2 text-[13px] text-red">
            {apiHata}
          </div>
        )}

        <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
          {isSubmitting ? 'Gönderiliyor…' : 'Gönder'}
        </button>
      </form>
    </div>
  );
}
