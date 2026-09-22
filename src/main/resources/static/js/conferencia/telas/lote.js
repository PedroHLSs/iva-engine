/* ---------------------------------------------------------------------------
   Resultado de um lote: uma tela diferente, e nao a da nota repetida n vezes.

   Quem trabalha no fiscal corrige cadastro, nao nota. Um NCM classificado
   errado aparece em quatrocentas notas e continua sendo UM erro de
   parametrizacao. Uma lista de quatrocentas linhas iguais mostra o tamanho do
   estrago e esconde a causa, e leva a pessoa a conferir quatrocentas vezes a
   mesma coisa.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../../dom.js';
import { inteiro } from '../../formato.js';
import * as api from '../api.js';
import {
  avisoDeUso, blocoDaLeitura, cabecalhoDoResultado, produtosComPendencia,
  quadroDeEstados, selo, valorOuMotivo,
} from '../pecas.js';
import { enderecoDeEnvio, enderecoDoGrupo } from '../roteador.js';

export async function desenhar(raiz, resposta, rota) {
  const corpo = el('div', {});
  trocar(raiz, [
    cabecalhoDoResultado(
      'Resultado do lote',
      inteiro(resposta.conferencia.quantidadeDeNotas) + ' nota(s), '
        + inteiro(resposta.conferencia.quantidadeDeProdutos) + ' produto(s).',
      resposta.natureza,
    ),
    corpo,
  ]);

  await desenharComOrdem(corpo, resposta, rota, rota.parametros.get('ordem') || null);
}

async function desenharComOrdem(corpo, resposta, rota, ordem) {
  trocar(corpo, [el('p', { classe: 'carregando', texto: 'Agrupando...' })]);

  const agrupado = await api.grupos(rota.analiseId, ordem);
  const conferencia = agrupado.conferencia;

  trocar(corpo, [
    quadroDeEstados(conferencia.produtosPorSituacao, 'Situacao dos produtos do lote'),
    produtosComPendencia(
      conferencia.produtosComAlgumaVerificacaoNaoConcluida,
      conferencia.quantidadeDeProdutos,
    ),

    blocoDaLeitura(agrupado.leitura),

    el('section', { classe: 'bloco' }, [
      el('h2', { texto: 'Grupos por parametrizacao' }),
      seletorDeOrdem(agrupado, (escolhida) =>
        desenharComOrdem(corpo, resposta, rota, escolhida)),
      el('p', { classe: 'nota significado-da-ordem', texto: agrupado.ordem.significado }),
      listaDeGrupos(agrupado.grupos, rota.analiseId),
    ]),

    el('div', { classe: 'acoes' }, [
      el('a', { classe: 'botao', href: enderecoDeEnvio(), texto: 'Enviar outro acervo' }),
    ]),
    avisoDeUso(agrupado.aviso),
  ]);
}

/**
 * A escolha de ordem, com o que cada uma mede escrito ao lado.
 *
 * A leitura natural de uma lista ordenada e "o de cima e o mais grave". Nao e:
 * a ordem padrao mede exposicao. Um grupo caro com erro trivial de preenchimento
 * sobe acima de um grupo barato que perdeu um beneficio. Por isso o significado
 * da ordem escolhida fica visivel, e nao dentro de um "?" de rodape.
 */
function seletorDeOrdem(agrupado, aoEscolher) {
  const botoes = agrupado.ordensDisponiveis.map((disponivel) => el('button', {
    classe: 'botao' + (disponivel.ordem === agrupado.ordem.ordem ? ' principal' : ''),
    'aria-pressed': disponivel.ordem === agrupado.ordem.ordem ? 'true' : 'false',
    title: disponivel.significado,
    texto: disponivel.rotulo,
    aoClicar: () => aoEscolher(disponivel.ordem),
  }));
  return el('div', { classe: 'acoes' }, [
    el('span', { classe: 'nota', texto: 'ordenar por:' }),
    ...botoes,
  ]);
}

function listaDeGrupos(grupos, analiseId) {
  if (!grupos.length) {
    return el('p', { classe: 'tabela-vazia', texto: 'Nenhum produto foi lido neste lote.' });
  }

  return el('div', { classe: 'grupos' }, grupos.map((grupo) => el('article', {
    classe: 'grupo',
  }, [
    el('div', { classe: 'grupo-chave' }, [
      el('span', { classe: 'grupo-rotulo', texto: 'NCM' }),
      valorOuMotivo(grupo.ncm, grupo.motivoDoNcmAusente),
      el('span', { classe: 'grupo-rotulo', texto: 'cClassTrib' }),
      valorOuMotivo(grupo.cClassTrib, grupo.motivoDoClassTribAusente),
      selo(grupo.situacao, grupo.rotuloDaSituacao, grupo.explicacaoDaSituacao),
    ]),

    /*
     * O nivel do agrupamento vai escrito. Grupos agrupados por criterios
     * diferentes nao sao comparaveis entre si, e apresenta-los na mesma lista
     * sem dizer isso seria somar coisas que nao se somam.
     */
    el('p', { classe: 'grupo-nivel', texto: grupo.rotuloDoNivel }),

    el('div', { classe: 'grupo-medida' }, [
      el('div', {}, [
        el('strong', { texto: inteiro(grupo.quantidadeDeProdutos) }),
        el('span', { texto: ' produto(s) em ' + inteiro(grupo.quantidadeDeNotas) + ' nota(s)' }),
      ]),
      el('div', {}, [
        el('strong', { classe: 'mono', texto: grupo.valorDosProdutos }),
        /*
         * O rotulo vem do servidor e nao e abreviado aqui. "Valor", sozinho,
         * numa coluna ao lado de "possivel divergencia", seria lido como
         * prejuizo — e nao e: e o tamanho da operacao envolvida.
         */
        el('span', { classe: 'rotulo-do-valor', texto: ' ' + grupo.rotuloDoValorDosProdutos }),
      ]),
    ]),

    contagensDoGrupo(grupo),

    el('div', { classe: 'acoes' }, [
      el('a', {
        classe: 'botao',
        href: enderecoDoGrupo(analiseId, grupo),
        texto: 'ver as notas e os itens',
      }),
    ]),
  ])));
}

/** As quatro contagens do grupo, inclusive as zeradas, e a linha de pendencia. */
function contagensDoGrupo(grupo) {
  const partes = grupo.verificacoesPorEstado.map((contagem) => el('span', {
    classe: 'contagem-estado',
  }, [
    el('strong', { texto: inteiro(contagem.quantidade) }),
    ' ' + contagem.rotulo,
  ]));

  return el('div', { classe: 'grupo-corpo' }, [
    el('p', { classe: 'nota', texto: 'verificacoes deste grupo:' }),
    el('div', { classe: 'contagens-inline' }, partes),
    el('p', {
      classe: 'nota',
      texto: inteiro(grupo.produtosComAlgumaVerificacaoNaoConcluida)
        + ' produto(s) deste grupo tem ao menos uma verificacao sem conclusao.',
    }),
  ]);
}
