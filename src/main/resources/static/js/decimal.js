/* ---------------------------------------------------------------------------
   Aritmetica decimal sobre texto, sem ponto flutuante.

   A API manda valor monetario como TEXTO, e a Etapa 8 explica por que: para a
   auditoria, "0" e "0,00" sao registros diferentes do mesmo numero, e
   JSON.parse("0.00") devolve 0, apagando a escala declarada pelo documento.

   Se a soma dos grupos passasse por Number, a pagina desfaria no cliente
   exatamente a distincao que o servidor tomou o cuidado de preservar. Entao a
   soma e feita em BigInt sobre os digitos, e a escala do resultado e a maior
   das parcelas.
   --------------------------------------------------------------------------- */

/** Quebra "-12.340" em { sinal, inteiro, fracao }. */
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

/** Indica se o texto e um decimal simples, do jeito que a API o escreve. */
export function ehDecimal(texto) {
  return typeof texto === 'string' && /^-?\d+(\.\d+)?$/.test(texto.trim());
}

/**
 * Soma dois decimais em texto, preservando a maior escala.
 *
 * somar("10.00", "0.5") devolve "10.50", nao "10.5": a casa declarada por uma
 * das parcelas nao se perde na soma.
 */
export function somar(a, b) {
  const x = partir(a);
  const y = partir(b);
  const escala = Math.max(x.fracao.length, y.fracao.length);

  const inteiroDe = (parte) =>
    BigInt((parte.negativo ? '-' : '') + parte.inteiro + parte.fracao.padEnd(escala, '0'));

  return formatarBruto(inteiroDe(x) + inteiroDe(y), escala);
}

/** Devolve um BigInt escalonado ao formato "123.45". */
function formatarBruto(total, escala) {
  const negativo = total < 0n;
  const digitos = (negativo ? -total : total).toString().padStart(escala + 1, '0');
  const inteiro = digitos.slice(0, digitos.length - escala);
  const fracao = escala === 0 ? '' : '.' + digitos.slice(digitos.length - escala);
  return (negativo ? '-' : '') + inteiro + fracao;
}

/**
 * O mesmo numero na notacao brasileira, com a escala que veio.
 *
 * "1000.00" vira "1.000,00" e "0" continua "0" — a pagina nao acrescenta nem
 * remove casa decimal, porque a escala e informacao do documento.
 */
export function comoQuantia(texto) {
  if (!ehDecimal(texto)) {
    return String(texto);
  }
  const { negativo, inteiro, fracao } = partir(texto);
  const comMilhar = inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return (negativo ? '-' : '') + comMilhar + (fracao ? ',' + fracao : '');
}

/**
 * O valor numerico, SOMENTE para ordenar.
 *
 * Nunca use isto para exibir: aqui a escala se perde de proposito, porque
 * comparar nao precisa dela. Exibir usa comoQuantia sobre o texto original.
 */
export function paraOrdenar(texto) {
  return ehDecimal(texto) ? Number(texto) : Number.NaN;
}
