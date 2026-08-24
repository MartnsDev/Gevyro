let audioContext: AudioContext | null = null;

/**
 * Prepara o áudio durante o clique do usuário e devolve uma função que toca
 * o som somente quando a venda ou o pedido for confirmado pela API.
 */
export function prepararSomDeVenda(): () => void {
  if (typeof window === "undefined") return () => undefined;

  try {
    audioContext ??= new AudioContext();
    const context = audioContext;
    void context.resume();

    return () => {
      try {
        void context.resume();
        const inicio = context.currentTime + 0.02;

        [
          { frequencia: 659.25, atraso: 0, duracao: 0.12 },
          { frequencia: 783.99, atraso: 0.1, duracao: 0.14 },
          { frequencia: 1046.5, atraso: 0.22, duracao: 0.3 },
        ].forEach(({ frequencia, atraso, duracao }) => {
          const oscilador = context.createOscillator();
          const volume = context.createGain();
          const comeco = inicio + atraso;
          const fim = comeco + duracao;

          oscilador.type = "sine";
          oscilador.frequency.setValueAtTime(frequencia, comeco);
          volume.gain.setValueAtTime(0.0001, comeco);
          volume.gain.exponentialRampToValueAtTime(0.16, comeco + 0.015);
          volume.gain.exponentialRampToValueAtTime(0.0001, fim);

          oscilador.connect(volume);
          volume.connect(context.destination);
          oscilador.start(comeco);
          oscilador.stop(fim);
        });
      } catch {
        // O áudio é um reforço visual; restrições do navegador não devem afetar a venda.
      }
    };
  } catch {
    return () => undefined;
  }
}
