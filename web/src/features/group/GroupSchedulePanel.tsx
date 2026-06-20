import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { ApiException } from '../../api/client';
import type { GrupTipi, ScheduleResponse } from '../../api/types';
import { GUN_LABEL, GUN_ORDER, toHm } from './scheduleDisplay';
import { scheduleSchema, toPayload, type ScheduleFormValues } from './scheduleSchema';
import {
  useCreateSchedule,
  useGroupSchedules,
  useSetScheduleActive,
  useUpdateSchedule,
} from './useSchedules';

const inputClass =
  'w-full rounded-[10px] border border-line bg-card px-3 py-2 text-[13.5px] focus:border-rasp focus:outline-none focus:ring-1 focus:ring-rasp';

const EMPTY: ScheduleFormValues = {
  gun: 'PAZARTESI',
  baslangicSaati: '',
  bitisSaati: '',
};

/** Program (haftalik ders saatleri) panonu. grupId + grup tipi (sol kenar rengi) + ADMIN gating. */
export default function GroupSchedulePanel({
  groupId,
  tip,
  isAdmin,
}: {
  groupId: number;
  tip: GrupTipi;
  isAdmin: boolean;
}) {
  const schedulesQuery = useGroupSchedules(groupId);
  const createMut = useCreateSchedule(groupId);
  const updateMut = useUpdateSchedule(groupId);
  const setActiveMut = useSetScheduleActive(groupId);

  const [showForm, setShowForm] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ScheduleFormValues>({
    resolver: zodResolver(scheduleSchema),
    defaultValues: EMPTY,
  });

  const lessonClass = tip === 'OZEL' ? 'lesson private' : 'lesson group';

  const schedules = [...(schedulesQuery.data?.data ?? [])].sort((a, b) => {
    const gd = GUN_ORDER.indexOf(a.gun) - GUN_ORDER.indexOf(b.gun);
    return gd !== 0 ? gd : a.baslangicSaati.localeCompare(b.baslangicSaati);
  });

  function openCreate() {
    setEditId(null);
    setFormError(null);
    reset(EMPTY);
    setShowForm(true);
  }

  function openEdit(s: ScheduleResponse) {
    setEditId(s.id);
    setFormError(null);
    reset({
      gun: s.gun,
      baslangicSaati: toHm(s.baslangicSaati),
      bitisSaati: toHm(s.bitisSaati),
    });
    setShowForm(true);
  }

  function closeForm() {
    setShowForm(false);
    setEditId(null);
    setFormError(null);
    reset(EMPTY);
  }

  async function onSubmit(values: ScheduleFormValues) {
    setFormError(null);
    const payload = toPayload(groupId, values);
    try {
      if (editId !== null) {
        await updateMut.mutateAsync({ id: editId, payload });
      } else {
        await createMut.mutateAsync(payload);
      }
      closeForm();
    } catch (e) {
      if (e instanceof ApiException) {
        if (e.code === 'VALIDATION_ERROR' && e.fields) {
          for (const [field, message] of Object.entries(e.fields)) {
            setError(field as keyof ScheduleFormValues, { message });
          }
          setFormError('Lütfen işaretli alanları düzeltin.');
        } else {
          // 409 CONFLICT (salon/öğretmen çakışması) dahil tüm form-düzeyi hatalar.
          setFormError(e.message);
        }
      } else {
        setFormError('Beklenmeyen bir hata oluştu.');
      }
    }
  }

  function onToggleActive(s: ScheduleResponse) {
    setActiveMut.mutate({ id: s.id, aktif: !s.aktif });
  }

  return (
    <section className="card space-y-3">
      <div className="flex items-center justify-between">
        <h3>Haftalık Program</h3>
        {isAdmin && !showForm && (
          <button type="button" className="btn btn-ghost" onClick={openCreate}>
            + Ders Saati Ekle
          </button>
        )}
      </div>

      {isAdmin && showForm && (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-3" noValidate>
          {formError && (
            <div className="rounded-[12px] border border-red/30 bg-red-soft px-4 py-2.5 text-[13px] font-semibold text-red">
              {formError}
            </div>
          )}
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-gray-700">Gün *</span>
              <select className={inputClass} {...register('gun')}>
                {GUN_ORDER.map((g) => (
                  <option key={g} value={g}>
                    {GUN_LABEL[g]}
                  </option>
                ))}
              </select>
              {errors.gun?.message && (
                <span className="mt-1 block text-xs text-red-600">{errors.gun.message}</span>
              )}
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-gray-700">
                Başlangıç *
              </span>
              <input type="time" className={inputClass} {...register('baslangicSaati')} />
              {errors.baslangicSaati?.message && (
                <span className="mt-1 block text-xs text-red-600">
                  {errors.baslangicSaati.message}
                </span>
              )}
            </label>
            <label className="block">
              <span className="mb-1 block text-sm font-medium text-gray-700">Bitiş *</span>
              <input type="time" className={inputClass} {...register('bitisSaati')} />
              {errors.bitisSaati?.message && (
                <span className="mt-1 block text-xs text-red-600">
                  {errors.bitisSaati.message}
                </span>
              )}
            </label>
          </div>
          <div className="flex justify-end gap-3">
            <button type="button" className="btn btn-ghost" onClick={closeForm}>
              İptal
            </button>
            <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
              {isSubmitting ? 'Kaydediliyor…' : editId !== null ? 'Güncelle' : 'Ekle'}
            </button>
          </div>
        </form>
      )}

      {schedulesQuery.isLoading ? (
        <p className="text-sm text-ink-soft">Yükleniyor…</p>
      ) : schedulesQuery.isError ? (
        <p className="text-sm text-red">
          {schedulesQuery.error instanceof ApiException
            ? schedulesQuery.error.message
            : 'Program yüklenemedi'}
        </p>
      ) : schedules.length === 0 ? (
        <p className="text-sm text-ink-soft">Tanımlı ders saati yok</p>
      ) : (
        <div className="schedule">
          {schedules.map((s) => (
            <div key={s.id} className={lessonClass} style={s.aktif ? undefined : { opacity: 0.55 }}>
              <div>
                <div className="time">{toHm(s.baslangicSaati)}</div>
              </div>
              <div>
                <div className="name">{GUN_LABEL[s.gun]}</div>
                <div className="meta">
                  {toHm(s.baslangicSaati)}–{toHm(s.bitisSaati)}
                  {!s.aktif && ' · Pasif'}
                </div>
              </div>
              {isAdmin && (
                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    className="btn btn-ghost"
                    onClick={() => openEdit(s)}
                  >
                    Düzenle
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    disabled={setActiveMut.isPending}
                    onClick={() => onToggleActive(s)}
                  >
                    {s.aktif ? 'Pasifleştir' : 'Aktifleştir'}
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  );
}
