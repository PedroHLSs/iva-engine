/* ---------------------------------------------------------------------------
   Analises anteriores, com volta para o resultado de cada uma.

   Uma segunda chamada por linha, e isso e deliberado.

   A listagem de execucoes traz apontamentos e mais nada. Uma lista de historico
   que mostrasse so "12 apontamentos" faria um lote com trinta arquivos
   ilegiveis e quarenta verificacoes sem conclusao parecer um lote quase limpo —
   que e exatamente a leitura que esta etapa existe para impedir. Entao cada
   linha pergunta pelos quatro estados dela.

   E a mesma decisao que a tela de execucoes da Etapa 9 tomou, pelo mesmo motivo,
   e o custo e o mesmo: e localhost, e sao vinte e cinco linhas.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../../dom.js';
import { dataHora, inteiro } from '../../formato.js';
import * as api from '../api.js';
import { falha } from '../pecas.js';
import { enderecoDeEnvio, enderecoDoResultado } from '../roteador.js';

const QUANTAS = 25;

/** As marcas de forma, na mesma ordem de precedencia que o servidor manda. */
const MARCA = {
  POSSIVEL_DIVERGENCIA: '▲',
  REQUER_CONFERENCIA: '◆',
  NAO_FOI_POSSIVEL_CONCLUIR: '■',
  SEM_DIVERGENCIA_IDENTIFICADA: '●',
};

const COR = {
  POSSIVEL_DIVERGENCIA: 'est-divergencia',
  REQUER_CONFERENCIA: 'est-conferencia',
  NAO_FOI_POSSIVEL_CONCLUIR: 'est-inconcluso',
  SEM_DIVERGENCIA_IDENTIFICADA: 'est-semdivergencia',
};

export async function desenhar(raiz) {
  const corpo = el('div', {}, [
    el('p', { classe: 'carregando', texto: 'Carregando o historico...' }),
  ]);

  trocar(raiz, [
    el('h1', { texto: 'Analises anteriores' }),
    el('p', {
      classe: 'sub',
      texto: 'As rodadas gravadas, da mais recente para a mais antiga. Abrir uma reabre o '
        + 'resultado dela, resolvido contra a carga de catalogo que ela registrou.',
    }),
    corpo,
    el('div', { classe: 'acoes' }, [
      el('a', { classe: 'botao', href: enderecoDeEnvio(), texto: 'Enviar uma nota' }),
      /*
       * A visao tecnica fica a um clique daqui, e o rotulo diz o que ela e sem
       * ambiguidade. E a tela que a banca vai pedir, e procurar por ela na
       * frente deles seria constrangedor.
       */
      el('a', {
        classe: 'botao',
        href: 'tecnica.html#/',
        texto: 'Visao tecnica (apontamentos por regra e severidade)',
      }),
    ]),
  ]);

  let listagem;
  try {
    listagem = await api.execucoes(QUANTAS);
  } catch (naoDeuCerto) {
    trocar(corpo, [falha(naoDeuCerto)]);
    return;
  }

  if (!listagem.execucoes.length) {
    trocar(corpo, [
      el('p', {
        classe: 'tabela-vazia',
        texto: 'Nenhuma analise gravada ainda. Envie uma nota para comecar.',
      }),
    ]);
    return;
  }

  const linhas = listagem.execucoes.map(() => el('tr', {}));
  trocar(corpo, [legenda(), tabela(linhas)]);

  for (let posicao = 0; posicao < listagem.execucoes.length; posicao += 1) {
    const execucao = listagem.execucoes[posicao];
    trocar(linhas[posicao], celulasCarregando(execucao));
    try {
      const resultado = await api.analise(execucao.id);
      trocar(linhas[posicao], celulas(execucao, resultado));
    } catch (naoDeuCerto) {
      trocar(linhas[posicao], celulasSemDetalhe(execucao, naoDeuCerto));
    }
  }
}

/**
 * A legenda das quatro marcas.
 *
 * A linha do historico e compacta e mostra marca e numero. Sem legenda, a marca
 * seria decoracao e a cor voltaria a ser a unica codificacao — que e justamente
 * o que a etapa proibe.
 */
function legenda() {
  const itens = [
    ['POSSIVEL_DIVERGENCIA', 'Possivel divergencia'],
    ['REQUER_CONFERENCIA', 'Requer conferencia'],
    ['NAO_FOI_POSSIVEL_CONCLUIR', 'Nao foi possivel concluir'],
    ['SEM_DIVERGENCIA_IDENTIFICADA', 'Sem divergencia identificada'],
  ];
  return el('p', { classe: 'legenda-estados' }, itens.map(([estado, rotulo]) =>
    el('span', { classe: 'contagem-estado ' + COR[estado] }, [
      el('span', { classe: 'marca', 'aria-hidden': 'true', texto: MARCA[estado] }),
      el('span', { texto: rotulo }),
    ])));
}

function tabela(linhas) {
  return el('div', { classe: 'rolagem' }, [
    el('table', { classe: 'tabela' }, [
      el('thead', {}, [el('tr', {}, [
        el('th', { texto: 'rodou em' }),
        el('th', { texto: 'acervo' }),
        el('th', { texto: 'situacao dos produtos' }),
        el('th', { texto: 'arquivos nao lidos' }),
        el('th', { texto: 'catalogo' }),
        el('th', { texto: '' }),
      ])]),
      el('tbody', {}, linhas),
    ]),
  ]);
}

function celulasCarregando(execucao) {
  return [
    el('td', { texto: dataHora(execucao.dataHora) }),
    el('td', { texto: acervo(execucao) }),
    el('td', { classe: 'nota', texto: 'carregando...' }),
    el('td', { texto: '' }),
    el('td', { classe: 'mono', texto: execucao.versaoCatalogo }),
    el('td', { texto: '' }),
  ];
}

function celulas(execucao, resultado) {
  const conferencia = resultado.conferencia;
  return [
    el('td', { texto: dataHora(execucao.dataHora) }),
    el('td', { texto: acervo(execucao) }),
    el('td', {}, [contagensInline(conferencia.produtosPorSituacao)]),
    el('td', {
      classe: resultado.leitura.arquivosIlegiveis > 0 ? 'destaque-ausencia numero' : 'numero',
      texto: inteiro(resultado.leitura.arquivosIlegiveis),
    }),
    el('td', { classe: 'mono', texto: execucao.versaoCatalogo }),
    el('td', {}, [
      el('a', { href: enderecoDoResultado(execucao.id), texto: 'abrir' }),
    ]),
  ];
}

/** Quando o detalhe nao vem, a linha diz isso — e nao finge um resumo. */
function celulasSemDetalhe(execucao, erro) {
  return [
    el('td', { texto: dataHora(execucao.dataHora) }),
    el('td', { texto: acervo(execucao) }),
    el('td', { classe: 'ausente', texto: 'nao foi possivel ler o resumo: ' + erro.message }),
    el('td', { classe: 'ausente', texto: 'idem' }),
    el('td', { classe: 'mono', texto: execucao.versaoCatalogo }),
    el('td', {}, [
      el('a', { href: enderecoDoResultado(execucao.id), texto: 'abrir' }),
    ]),
  ];
}

function acervo(execucao) {
  return inteiro(execucao.quantidadeDocumentos) + ' doc. / '
    + inteiro(execucao.quantidadeItens) + ' itens';
}

/**
 * Os quatro numeros na linha, inclusive os zeros.
 *
 * Sem soma nenhuma, e sem esconder o inconcluso: ele tem a mesma marca e o
 * mesmo peso dos outros tres.
 */
function contagensInline(contagens) {
  return el('span', { classe: 'contagens-inline' }, (contagens || []).map((contagem) =>
    el('span', {
      classe: 'contagem-estado ' + (COR[contagem.estado] || 'est-neutro'),
      title: contagem.rotulo + ' — ' + contagem.explicacao,
    }, [
      el('span', { classe: 'marca', 'aria-hidden': 'true', texto: MARCA[contagem.estado] || '○' }),
      el('strong', { texto: inteiro(contagem.quantidade) }),
      el('span', { classe: 'so-leitor-de-tela', texto: ' ' + contagem.rotulo }),
    ])));
}
