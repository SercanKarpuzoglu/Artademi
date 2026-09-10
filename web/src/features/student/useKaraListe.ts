import { useMutation, useQueryClient } from '@tanstack/react-query';
import { setKaraListe } from '../../api/students';

/** Kara listeye al / çıkar; öğrenci detayı ve listeler tazelenir. */
export function useKaraListe(studentId: number) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (payload: { karaListe: boolean; aciklama?: string }) =>
      setKaraListe(studentId, payload),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['student', studentId] });
      qc.invalidateQueries({ queryKey: ['students'] });
      qc.invalidateQueries({ queryKey: ['student-liste'] });
    },
  });
}
