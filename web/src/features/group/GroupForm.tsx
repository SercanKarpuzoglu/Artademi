import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiException } from '../../api/client';
import type { GroupResponse } from '../../api/types';
import { useBranches } from '../branch/useBranches';
import { useRooms } from '../room/useRooms';
import { useTeachers } from '../teacher/useTeachers';
import { GroupFormValues, groupSchema, toPayload } from './groupSchema';
import { useCreateGroup, useGroup, useUpdateGroup } from './useGroups';

const EMPTY: GroupFormValues = {
  ad: '',
  tip: 'GRUP',
  bransId: 0,
  ogretmenId: 0,
  salonId: undefined,
  seviye: '',
  aylikAidat: '',
  dersBasiUcret: '',
};

function toFormValues(g: GroupResponse): GroupFormValues {
  const money = (x: string | number | null) =>
    x === null || x === undefined ? '' : String(x);
  return {
    ad: g.ad,
    tip: g.tip,
    bransId: g.brans?.id ?? 0,
    ogretmenId: g.ogretmen?.id ?? 0,
    salonId: g.salon?.id ?? undefined,
    seviye: g.seviye ?? '',
    aylikAidat: money(g.aylikAidat),
    dersBasiUcret: money(g.dersBasiUcret),
  };
}

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

export default function GroupForm() {
  const params = useParams<{ id?: string }>();
  const id = params.id ? Number(params.id) : undefined;
  const isEdit = id !== undefined;
  const navigate = useNavigate();

  const [formError, setFormError] = useState<string | null>(null);

  const groupQuery = useGroup(id);
  const createMut = useCreateGroup();
  const updateMut = useUpdateGroup(id ?? 0);

  // Dropdown kaynaklari — yalnizca aktif kayitlar (buyuk size ile hepsini al).
  const branchQuery = useBranches({ aktif: true, size: 200 });
  const teacherQuery = useTeachers({ aktif: true, size: 200 });
  const roomQuery = useRooms({ aktif: true, size: 200 });

  const {
    register,
    handleSubmit,
    reset,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<GroupFormValues>({
    resolver: zodResolver(groupSchema),
    defaultValues: EMPTY,
  });

  useEffect(() => {
    if (isEdit && groupQuery.data) {
      reset(toFormValues(groupQuery.data));
    }
  }, [isEdit, groupQuery.data, reset]);

  const tip = watch('tip');

  const loaded = isEdit ? groupQuery.data : undefined;

  // Aktif listeler. Duzenlemede secili kayit pasifse listede olmayabilir; o durumda
  // GroupResponse referansindan sentezleyip listeye ekleriz ki secili deger gorunsun.
  const branchOptions = branchQuery.data?.data ?? [];
  const teacherOptions = teacherQuery.data?.data ?? [];
  const roomOptions = roomQuery.data?.data ?? [];

  const branchList =
    loaded?.brans && !branchOptions.some((b) => b.id === loaded.brans!.id)
      ? [{ id: loaded.brans.id, ad: loaded.brans.ad }, ...branchOptions]
      : branchOptions;
  const teacherList =
    loaded?.ogretmen && !teacherOptions.some((t) => t.id === loaded.ogretmen!.id)
      ? [
          {
            id: loaded.ogretmen.id,
            ad: loaded.ogretmen.ad,
            soyad: loaded.ogretmen.soyad,
          },
          ...teacherOptions,
        ]
      : teacherOptions;
  const roomList =
    loaded?.salon && !roomOptions.some((r) => r.id === loaded.salon!.id)
      ? [{ id: loaded.salon.id, ad: loaded.salon.ad }, ...roomOptions]
      : roomOptions;

  async function onSubmit(values: GroupFormValues) {
    setFormError(null);
    try {
      const payload = toPayload(values);
      if (isEdit) {
        await updateMut.mutateAsync(payload);
      } else {
        await createMut.mutateAsync(payload);
      }
      navigate('/gruplar');
    } catch (e) {
      if (e instanceof ApiException) {
        if (e.code === 'VALIDATION_ERROR' && e.fields) {
          for (const [field, message] of Object.entries(e.fields)) {
            setError(field as keyof GroupFormValues, { message });
          }
          setFormError('Lütfen işaretli alanları düzeltin.');
        } else {
          setFormError(`${e.message}`);
        }
      } else {
        setFormError('Beklenmeyen bir hata oluştu.');
      }
    }
  }

  if (isEdit && groupQuery.isLoading) {
    return <div className="card text-center text-ink-soft">Yükleniyor…</div>;
  }
  if (isEdit && groupQuery.isError) {
    return (
      <div className="card text-center text-red">
        {groupQuery.error instanceof ApiException ? groupQuery.error.message : 'Grup yüklenemedi'}
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-3xl">
      <div className="topbar">
        <div>
          <h1>{isEdit ? 'Grup Düzenle' : 'Yeni Grup'}</h1>
          <div className="sub">Grup/özel ders bilgileri, branş, öğretmen ve ücret</div>
        </div>
      </div>

      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
        {formError && (
          <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
            {formError}
          </div>
        )}

        <section className="card space-y-4">
          <h2 className="text-sm font-semibold text-gray-700">Genel Bilgiler</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Field label="Ad" required error={errors.ad?.message}>
              <input className={inputClass} {...register('ad')} />
            </Field>
            <Field label="Tip" required error={errors.tip?.message}>
              <select className={inputClass} {...register('tip')}>
                <option value="GRUP">Grup</option>
                <option value="OZEL">Özel</option>
              </select>
            </Field>
            <Field label="Branş" required error={errors.bransId?.message}>
              <select
                className={inputClass}
                {...register('bransId', { setValueAs: (v) => (v ? Number(v) : 0) })}
              >
                <option value="">Seçiniz…</option>
                {branchList.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.ad}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Öğretmen" required error={errors.ogretmenId?.message}>
              <select
                className={inputClass}
                {...register('ogretmenId', { setValueAs: (v) => (v ? Number(v) : 0) })}
              >
                <option value="">Seçiniz…</option>
                {teacherList.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.ad} {t.soyad}
                  </option>
                ))}
              </select>
            </Field>
            <Field
              label="Salon"
              required={tip === 'GRUP'}
              error={errors.salonId?.message}
            >
              <select
                className={inputClass}
                {...register('salonId', {
                  setValueAs: (v) => (v ? Number(v) : undefined),
                })}
              >
                <option value="">Seçiniz…</option>
                {roomList.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.ad}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Seviye" error={errors.seviye?.message}>
              <input className={inputClass} {...register('seviye')} />
            </Field>
          </div>
        </section>

        <section className="card space-y-4">
          <h2 className="text-sm font-semibold text-gray-700">Ücretlendirme</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            {tip === 'GRUP' ? (
              <Field label="Aylık Aidat (₺)" required error={errors.aylikAidat?.message}>
                <input className={inputClass} inputMode="decimal" {...register('aylikAidat')} />
              </Field>
            ) : (
              <Field label="Ders Başı Ücret (₺)" required error={errors.dersBasiUcret?.message}>
                <input className={inputClass} inputMode="decimal" {...register('dersBasiUcret')} />
              </Field>
            )}
          </div>
        </section>

        <div className="flex justify-end gap-3">
          <button type="button" className="btn btn-ghost" onClick={() => navigate('/gruplar')}>
            İptal
          </button>
          <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
            {isSubmitting ? 'Kaydediliyor…' : 'Kaydet'}
          </button>
        </div>
      </form>
    </div>
  );
}

interface FieldProps {
  label: string;
  error?: string;
  required?: boolean;
  children: React.ReactNode;
}

function Field({ label, error, required, children }: FieldProps) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-gray-700">
        {label}
        {required && <span className="text-red-600"> *</span>}
      </span>
      {children}
      {error && <span className="mt-1 block text-xs text-red-600">{error}</span>}
    </label>
  );
}
