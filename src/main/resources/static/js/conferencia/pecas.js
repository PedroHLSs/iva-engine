/* ---------------------------------------------------------------------------
   As pecas que se repetem entre as telas de conferencia.

   Este arquivo e onde moram as quatro regras de apresentacao da etapa. Elas nao
   estao espalhadas pelas telas de proposito: espalhadas, a tela nova esquece uma
   e ninguem percebe.

   1. "Nao foi possivel concluir" tem o MESMO peso visual dos outros tres. Nao e
      cinza, nao esta atras de um clique, nao sai do resumo.
   2. O resumo mostra sempre os quatro numeros, inclusive os zeros.
   3. Cor nunca e a unica codificacao: todo estado sai com marca de forma e com
      o rotulo por extenso, os dois vindos do servidor.
   4. Em lugar nenhum um produto nao avaliado e somado aos sem divergencia.
      Aqui isso e estrutural: nenhuma funcao deste arquivo soma contagens.
   --------------------------------------------------------------------------- */

import { el } from '../dom.js';
import { inteiro } from '../formato.js';

/**
 * A marca de forma de cada estado.
 *
 * NAO ha visto de certo em lugar nenhum desta tabela, e a ausencia e deliberada:
 * um "check" ao lado de "sem divergencia identificada" seria lido como
 * "conferido", que e exatamente a afirmacao que o sistema nao faz. As quatro
 * marcas sao geometricas e nao carregam juizo.
 */
const MARCA = {
  POSSIVEL_DIVERGENCIA: '▲',
  REQUER_CONFERENCIA: '◆',
  NAO_FOI_POSSIVEL_CONCLUIR: '■',
  SEM_DIVERGENCIA_IDENTIFICADA: '●',
};

/** Classe de cor de cada estado. A cor acompanha o rotulo; nunca o substitui. */
const COR = {
  POSSIVEL_DIVERGENCIA: 'est-divergencia',
  REQUER_CONFERENCIA: 'est-conferencia',
  NAO_FOI_POSSIVEL_CONCLUIR: 'est-inconcluso',
  SEM_DIVERGENCIA_IDENTIFICADA: 'est-semdivergencia',
};

/** O selo de um estado: marca de forma, rotulo por extenso, cor por ultimo. */
export function selo(estado, rotulo, explicacao) {
  return el('span', {
    classe: 'selo-estado ' + (COR[estado] || 'est-neutro'),
    title: explicacao || null,
  }, [
    el('span', { classe: 'marca', 'aria-hidden': 'true', texto: MARCA[estado] || '○' }),
    el('span', { texto: rotulo || estado }),
  ]);
}

/**
 * O quadro dos quatro estados.
 *
 * Recebe a lista que o servidor manda — ja na ordem de precedencia, ja com
 * rotulo e explicacao — e desenha uma celula por estado, na mesma caixa e com a
 * mesma tipografia. Zero e desenhado como qualquer outro numero.
 *
 * Nenhuma soma acontece aqui. Nao ha total, nao ha "conformes + nao avaliados",
 * nao ha porcentagem sobre subconjunto. Se um dia fizer falta um total, ele
 * precisa nascer de uma decisao, e nao de um reduce que alguem escreveu sem
 * pensar no que estava somando.
 */
export function quadroDeEstados(contagens, titulo) {
  const celulas = (contagens || []).map((contagem) => el('div', {
    classe: 'estado-celula ' + (COR[contagem.estado] || 'est-neutro'),
  }, [
    el('div', { classe: 'estado-numero', texto: inteiro(contagem.quantidade) }),
    el('div', { classe: 'estado-rotulo' }, [
      el('span', { classe: 'marca', 'aria-hidden': 'true', texto: MARCA[contagem.estado] || '○' }),
      el('span', { texto: contagem.rotulo }),
    ]),
    el('p', { classe: 'estado-explicacao', texto: contagem.explicacao }),
  ]));

  return el('section', { classe: 'bloco' }, [
    titulo ? el('h2', { texto: titulo }) : null,
    el('div', { classe: 'estados' }, celulas),
  ]);
}

/**
 * O terceiro numero, que so existe porque a precedencia esconde a pendencia.
 *
 * Um produto com uma divergencia e tres verificacoes sem conclusao aparece como
 * "possivel divergencia" na coluna de situacao — a mais forte prevalece. A
 * pendencia dele sumiria da tela se nao houvesse esta linha.
 */
export function produtosComPendencia(quantidade, total) {
  return el('p', { classe: 'linha-pendencia' }, [
    el('strong', { texto: inteiro(quantidade) }),
    ' de ' + inteiro(total) + ' produto(s) tem ao menos uma verificacao sem conclusao, '
      + 'inclusive entre os que aparecem com outra situacao acima.',
  ]);
}

/**
 * A faixa de procedencia da carga.
 *
 * Vem do servidor inteira — codigo, rotulo, explicacao e a lista de tabelas
 * ficticias. A pagina nao decide quando avisar: ela obedece a `exigeAviso`.
 */
export function faixaDeNatureza(natureza) {
  if (!natureza) {
    return null;
  }
  const tabelas = natureza.tabelasFicticias || [];
  return el('div', {
    classe: 'faixa-natureza' + (natureza.exigeAviso ? ' avisa' : ''),
  }, [
    el('strong', { texto: natureza.rotulo }),
    el('p', { texto: natureza.explicacao }),
    tabelas.length
      ? el('p', { classe: 'nota', texto: 'Tabelas de demonstracao: ' + tabelas.join(', ') + '.' })
      : null,
    el('p', { classe: 'nota', texto: 'Carga de catalogo: ' + natureza.versaoDoCatalogo }),
  ]);
}

/**
 * O aviso de uso, em toda tela de resultado.
 *
 * Discreto e sempre acessivel, e o texto vem do servidor. Se morasse aqui, cada
 * tela nova precisaria lembrar de escreve-lo, e a que esquecesse nao quebraria
 * nada — ficaria so sem aviso.
 */
export function avisoDeUso(texto) {
  return el('p', { classe: 'aviso-de-uso', texto: texto || '' });
}

/**
 * O bloco de leitura: arquivos que entraram e os que nao puderam ser abertos.
 *
 * Separado do quadro de estados, e nunca dentro dele. Arquivo ilegivel nao e
 * nota sem divergencia: e ausencia, e nao existe celula em que soma-lo.
 */
export function blocoDaLeitura(leitura) {
  const ilegiveis = leitura.arquivosQueNaoForamLidos || [];
  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Leitura' }),
    el('dl', { classe: 'identificacao' }, [
      el('dt', { texto: 'documentos lidos' }),
      el('dd', { texto: inteiro(leitura.documentosLidos) }),
      el('dt', { texto: 'itens lidos' }),
      el('dd', { texto: inteiro(leitura.itensLidos) }),
      el('dt', { texto: 'arquivos que nao puderam ser lidos' }),
      el('dd', { classe: leitura.arquivosIlegiveis > 0 ? 'destaque-ausencia' : null,
        texto: inteiro(leitura.arquivosIlegiveis) }),
    ]),
    el('p', { classe: 'nota', texto: leitura.comoFoiALeitura }),
    ilegiveis.length
      ? el('ul', { classe: 'lista-ilegiveis' }, ilegiveis.map((arquivo) => el('li', {}, [
        el('span', { classe: 'mono', texto: arquivo.origem }),
        el('p', { classe: 'nota', texto: arquivo.motivo }),
      ])))
      : null,
  ]);
}

/** Um valor, ou o motivo de nao haver valor. Nunca branco. */
export function valorOuMotivo(valor, motivo) {
  if (valor !== null && valor !== undefined && valor !== '') {
    return el('span', { texto: String(valor) });
  }
  return el('span', {
    classe: 'ausente',
    texto: motivo || 'ausente, e a resposta nao disse por que',
  });
}

/** Uma leitura do catalogo: o conteudo, ou o motivo de nao haver conteudo. */
export function leituraDoCatalogo(leitura, comoDesenhar) {
  if (!leitura) {
    return el('p', { classe: 'ausente', texto: 'a resposta nao trouxe este bloco' });
  }
  const encontrado = leitura.encontrado || [];
  if (encontrado.length === 0) {
    return el('p', { classe: 'ausente', texto: leitura.motivoDaAusencia });
  }
  return comoDesenhar(encontrado);
}

/** Uma falha, dita por inteiro. */
export function falha(erro) {
  const partes = [
    el('strong', { texto: 'Nao foi possivel continuar. ' }),
    erro.message || String(erro),
  ];
  if (erro.detalhe) {
    partes.push(el('pre', { texto: erro.detalhe }));
  }
  return el('div', { classe: 'aviso erro' }, partes);
}

/** Cabecalho comum das telas de resultado: titulo, faixa e voltar. */
export function cabecalhoDoResultado(titulo, subtitulo, natureza) {
  return el('header', { classe: 'cabecalho-resultado' }, [
    el('h1', { texto: titulo }),
    subtitulo ? el('p', { classe: 'sub', texto: subtitulo }) : null,
    faixaDeNatureza(natureza),
  ]);
}
