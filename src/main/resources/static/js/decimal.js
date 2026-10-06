function partir(texto) {
  const limpo = String(texto).trim();
  const negativo = limpo.startsWith('-');
  const semSinal = negativo ? limpo.slice(1) : limpo;
  const ponto = semSinal.indexOf('.');
  return {
    negativo,
    inteiro: ponto < 0 ? semSinal : semSinal.slice(0, ponto),
    fracao: ponto < 0 ? '' : semSinal.slice(ponto + 1),
  };
}

export function ehDecimal(texto) {
  return typeof texto === 'string' && /^-?\d+(\.\d+)?$/.test(texto.trim());
}

export function somar(a, b) {
  const x = partir(a);
  const y = partir(b);
  const escala = Math.max(x.fracao.length, y.fracao.length);

  const inteiroDe = (parte) =>
    BigInt((parte.negativo ? '-' : '') + parte.inteiro + parte.fracao.padEnd(escala, '0'));

  return formatarBruto(inteiroDe(x) + inteiroDe(y), escala);
}

function formatarBruto(total, escala) {
  const negativo = total < 0n;
  const digitos = (negativo ? -total : total).toString().padStart(escala + 1, '0');
  const inteiro = digitos.slice(0, digitos.length - escala);
  const fracao = escala === 0 ? '' : '.' + digitos.slice(digitos.length - escala);
  return (negativo ? '-' : '') + inteiro + fracao;
}

export function comoQuantia(texto) {
  if (!ehDecimal(texto)) {
    return String(texto);
  }
  const { negativo, inteiro, fracao } = partir(texto);
  const comMilhar = inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return (negativo ? '-' : '') + comMilhar + (fracao ? ',' + fracao : '');
}

export function paraOrdenar(texto) {
  return ehDecimal(texto) ? Number(texto) : Number.NaN;
}
