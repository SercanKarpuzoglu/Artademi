import { QueryClient } from '@tanstack/react-query';

// Makul varsayilanlar: tek retry, kisa staleTime (yazma sonrasi tazeleme invalidate ile).
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 10_000,
      refetchOnWindowFocus: false,
    },
  },
});
