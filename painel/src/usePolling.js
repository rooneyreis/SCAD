import { useEffect, useRef, useState } from 'react';

/**
 * Executa `funcao` agora e a cada `intervaloMs`. Devolve [dados, erro, recarregar].
 * Os dados anteriores mantêm-se enquanto a próxima leitura não chega (o ecrã não "pisca").
 */
export function usePolling(funcao, intervaloMs, deps = []) {
  const [dados, setDados] = useState(null);
  const [erro, setErro] = useState(null);
  const ref = useRef(funcao);
  ref.current = funcao;
  const [tique, setTique] = useState(0);

  useEffect(() => {
    let vivo = true;
    const ler = async () => {
      try {
        const d = await ref.current();
        if (vivo) {
          setDados(d);
          setErro(null);
        }
      } catch (e) {
        if (vivo) setErro(e.message);
      }
    };
    ler();
    const id = setInterval(ler, intervaloMs);
    return () => {
      vivo = false;
      clearInterval(id);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervaloMs, tique, ...deps]);

  return [dados, erro, () => setTique((t) => t + 1)];
}
