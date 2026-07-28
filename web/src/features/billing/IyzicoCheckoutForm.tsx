import { useEffect, useRef } from 'react';

/**
 * iyzico'nun döndürdüğü checkoutFormContent'i (div + script) sayfaya gömer.
 *
 * innerHTML ile eklenen <script> etiketleri tarayıcıda ÇALIŞMAZ; bu yüzden içerik parse edilip
 * script'ler elle yeniden oluşturularak eklenir (iyzico'nun resmî embed davranışı). Kart formu
 * iyzico'nun iframe'inde açılır — kart verisi hiçbir zaman bizim DOM/state'imize girmez.
 */
export default function IyzicoCheckoutForm({ content }: { content: string }) {
  const hostRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    host.innerHTML = '';

    const template = document.createElement('template');
    template.innerHTML = content;

    template.content.querySelectorAll('script').forEach((eski) => {
      const yeni = document.createElement('script');
      Array.from(eski.attributes).forEach((a) => yeni.setAttribute(a.name, a.value));
      yeni.text = eski.text;
      eski.replaceWith(yeni);
    });
    // Not: template.content'ten host'a taşınırken script node'ları canlı DOM'a girdiği anda çalışır.
    host.appendChild(template.content);

    return () => {
      host.innerHTML = '';
    };
  }, [content]);

  return <div ref={hostRef} className="iyzico-form" />;
}
