import { el } from './dom.js';

export const INDEFINIDA = '(indefinida)';

const RELOGIO = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit', month: '2-digit', year: 'numeric',
  hour: '2-digit', minute: '2-digit', second: '2-digit',
});

export function dataHora(iso) {
  if (!iso) {
    return '';
  }
  const quando = new Date(iso);
  return Number.isNaN(quando.getTime()) ? String(iso) : RELOGIO.format(quando);
}

export function data(iso) {
  if (!iso) {
    return '';
  }
  const partes = String(iso).split('-');
  return partes.length === 3 ? `${partes[2]}/${partes[1]}/${partes[0]}` : String(iso);
}

export function inteiro(numero) {
  return Number(numero).toLocaleString('pt-BR');
}

export function ausente(motivo) {
  return el('span', {
    classe: 'ausente',
    texto: motivo || 'ausente, e a resposta nao disse por que',
  });
}

export function metrica(texto) {
  if (texto === undefined || texto === null || texto === '' || texto === INDEFINIDA) {
    return el('span', { classe: 'indefinida', texto: INDEFINIDA });
  }
  return el('span', { classe: 'mono', texto: String(texto) });
}

export function rotuloDaRegra(linha) {
  return el('span', { classe: 'regra-rotulo' }, [
    linha.regraNome
      ? el('span', { classe: 'regra-nome', texto: linha.regraNome })
      : ausente(linha.motivoDoNomeDaRegraAusente),
    ' ',
    el('span', { classe: 'regra-codigo', texto: linha.regraId }),
  ]);
}

export function regraEmTexto(linha) {
  return linha.regraNome ? linha.regraNome + ' (' + linha.regraId + ')' : linha.regraId;
}

export function severidade(nome) {
  return el('span', { classe: `sev sev-${nome}`, texto: nome });
}

export function chipDesfecho(desfecho, texto) {
  const classes = {
    ACHADO: 'chip chip-achado',
    NAO_AVALIADO: 'chip chip-naoavaliado',
    CONFORME: 'chip chip-conforme',
  };
  return el('span', { classe: classes[desfecho] || 'chip chip-neutro', texto });
}

export function documentoCurto(documento) {
  const modelo = documento.modelo || '?';
  const serie = documento.serie || '?';
  const numero = documento.numero || '?';
  return `${modelo}/${serie}/${numero}`;
}
