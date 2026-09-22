/* ---------------------------------------------------------------------------
   Formatacao de data, numero e ausencia.

   A regra que atravessa este arquivo: ausencia e ESCRITA. Nenhuma funcao aqui
   devolve string vazia, traco solto ou zero para representar "nao ha". Quando
   nao ha valor, sai o motivo que a propria resposta trouxe, em italico e com
   marcacao propria, porque campo em branco meses depois e indistinguivel de
   campo que ninguem preencheu.
   --------------------------------------------------------------------------- */

import { el } from './dom.js';

/** O que se escreve no lugar de metrica sem denominador. O mesmo texto do CSV. */
export const INDEFINIDA = '(indefinida)';

const RELOGIO = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit', month: '2-digit', year: 'numeric',
  hour: '2-digit', minute: '2-digit', second: '2-digit',
});

/** Instante ISO ("2026-09-10T14:32:07Z") no fuso de quem le. */
export function dataHora(iso) {
  if (!iso) {
    return '';
  }
  const quando = new Date(iso);
  return Number.isNaN(quando.getTime()) ? String(iso) : RELOGIO.format(quando);
}

/**
 * Data civil ("2026-09-10") como "10/09/2026".
 *
 * Nao passa por Date de proposito: data de emissao e data de vigencia sao
 * LocalDate, sem fuso, e construir um Date a partir delas desloca o dia para
 * quem estiver a oeste de Greenwich. Uma vigencia que comeca em 01/01 nao pode
 * virar 31/12 por causa do relogio da maquina.
 */
export function data(iso) {
  if (!iso) {
    return '';
  }
  const partes = String(iso).split('-');
  return partes.length === 3 ? `${partes[2]}/${partes[1]}/${partes[0]}` : String(iso);
}

/** Inteiro com separador de milhar. */
export function inteiro(numero) {
  return Number(numero).toLocaleString('pt-BR');
}

/**
 * Um valor ausente, escrito.
 *
 * Recebe o motivo que a resposta trouxe no campo irmao e o devolve como no
 * marcado. Se quem chamou nao tiver motivo nenhum, isso e defeito de quem
 * chamou, e a funcao diz isso em vez de imprimir branco.
 */
export function ausente(motivo) {
  return el('span', {
    classe: 'ausente',
    texto: motivo || 'ausente, e a resposta nao disse por que',
  });
}

/** Metrica de acuracia: o numero, ou "(indefinida)" com o porque ao lado. */
export function metrica(texto) {
  if (texto === undefined || texto === null || texto === '' || texto === INDEFINIDA) {
    return el('span', { classe: 'indefinida', texto: INDEFINIDA });
  }
  return el('span', { classe: 'mono', texto: String(texto) });
}

/**
 * A regra por extenso, com o codigo ao lado.
 *
 * Acrescentado depois da Etapa 11: as telas escreviam so "R06", que diz a quem
 * programa qual classe rodou e nao diz nada a quem le o resultado.
 *
 * O nome vem do servidor, em regraNome. Esta pagina continua sem tabela nenhuma
 * dizendo o que cada regra e (D010). O codigo continua escrito, menor, porque e
 * ele que a CLI aceita em --regra, que a planilha escreve e que o gabarito usa.
 * Sem nome, sai o motivo que a resposta trouxe no campo irmao - nunca o codigo
 * repetido fingindo ser nome.
 *
 * Recebe a linha inteira: achado, nao avaliado, linha por regra e passo da
 * conferencia usam os mesmos tres nomes de campo.
 */
export function rotuloDaRegra(linha) {
  return el('span', { classe: 'regra-rotulo' }, [
    linha.regraNome
      ? el('span', { classe: 'regra-nome', texto: linha.regraNome })
      : ausente(linha.motivoDoNomeDaRegraAusente),
    ' ',
    el('span', { classe: 'regra-codigo', texto: linha.regraId }),
  ]);
}

/**
 * A regra como texto corrido, para onde no nao cabe: opcao de select e frase.
 *
 * Sem nome, devolve so o codigo. Quem usa isto numa frase ja tem o rotulo
 * completo, com o motivo da ausencia, desenhado perto.
 */
export function regraEmTexto(linha) {
  return linha.regraNome ? linha.regraNome + ' (' + linha.regraId + ')' : linha.regraId;
}

/** Severidade com cor e triangulos: cor nunca sozinha. */
export function severidade(nome) {
  return el('span', { classe: `sev sev-${nome}`, texto: nome });
}

/** Chip de desfecho, com simbolo alem da cor. */
export function chipDesfecho(desfecho, texto) {
  const classes = {
    ACHADO: 'chip chip-achado',
    NAO_AVALIADO: 'chip chip-naoavaliado',
    CONFORME: 'chip chip-conforme',
  };
  return el('span', { classe: classes[desfecho] || 'chip chip-neutro', texto });
}

/** Documento como ele pode ser mostrado: pseudonimo e a localizacao da nota. */
export function documentoCurto(documento) {
  const modelo = documento.modelo || '?';
  const serie = documento.serie || '?';
  const numero = documento.numero || '?';
  return `${modelo}/${serie}/${numero}`;
}
