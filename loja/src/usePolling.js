import { useEffect, useRef, useState } from 'react';

/** Executa `funcao` agora e a cada `intervaloMs`; devolve [dados, erro]. Mantém os dados anteriores entre leituras. */
export function usePolling(funcao, intervaloMs, deps = []) {
  const [dados, setDados] = useState(null);
  const [erro, setErro] = useState(null);
  const ref = useRef(funcao);
  ref.current = funcao;
  useEffect(() => {
    let vivo = true;
    const ler = async () => {
      try {
        const d = await ref.current();
        if (vivo) { setDados(d); setErro(null); }
      } catch (e) {
        if (vivo) setErro(e);
      }
    };
    ler();
    const id = setInterval(ler, intervaloMs);
    return () => { vivo = false; clearInterval(id); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervaloMs, ...deps]);
  return [dados, erro];
}
