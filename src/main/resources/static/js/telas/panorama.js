/* ---------------------------------------------------------------------------
   Tela 2 - Panorama da execucao.

   A TELA INTEIRA E A REGRA 2 DA APRESENTACAO

   "R04: 0 apontamentos" precisa ser distinguivel de "R04: nao avaliado" de
   relance. Aqui a distincao aparece quatro vezes na mesma linha, de propósito e
   sem depender de cor:

     - a coluna "nao avaliados" traz o numero, sempre, inclusive zero;
     - o selo escreve "avaliou tudo" ou "nem tudo foi avaliado";
     - o grafico desenha faixa de nao avaliado com hachura propria, e um traco
       curto quando a regra nao apontou nem deixou pendencia;
     - os motivos aparecem logo abaixo, com quantas vezes cada um ocorreu.

   Uma regra que apontou zero porque avaliou tudo e uma regra que apontou zero
   porque desistiu de tudo sao situacoes opostas para quem vai decidir o que
   corrigir, e a tela nunca as desenha iguais.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../dom.js';
import { dataHora, inteiro, rotuloDaRegra } from '../formato.js';
import { identificacao, navegacaoDaExecucao, painelDaPlanilha, falha } from '../comum.js';
import { endereco } from '../roteador.js';
import { barraDeDesfechos, barrasPorRegra } from '../svg.js';
import * as api from '../api.js';

export async function desenhar(tela, parametros, rota) {
  trocar(tela, [el('p', { classe: 'carregando', texto: 'Lendo a execucao...' })]);

  let execucao;
  try {
    execucao = await api.execucao(rota.execucaoId);
  } catch (erro) {
    trocar(tela, [
      el('a', { classe: 'voltar', href: endereco('execucoes'), texto: 'Execucoes' }),
      falha(erro),
    ]);
    return;
  }

  const planilha = painelDaPlanilha(execucao.id);
  const d = execucao.desfechos;
  const conforme = d.conforme.valor === null ? null : Number(d.conforme.valor);

  trocar(tela, [
    el('a', { classe: 'voltar', href: endereco('execucoes'), texto: 'Execucoes' }),
    el('div', { classe: 'cartao-titulo' }, [
      el('h1', { texto: 'Panorama de ' + dataHora(execucao.dataHora) }),
      el('div', { classe: 'acoes' }, [planilha.botao]),
    ]),
    planilha.painel,
    navegacaoDaExecucao(execucao.id, 'panorama'),
    el('section', { classe: 'cartao' }, [identificacao(execucao)]),

    el('h3', { texto: 'os tres desfechos' }),
    el('div', { classe: 'desfechos' }, [
      cartaoDeDesfecho('d-achado', 'apontamentos', d.achado,
        'o que a auditoria levantou', endereco('achados', execucao.id)),
      cartaoDeDesfecho('d-naoavaliado', 'nao avaliados', d.naoAvaliado,
        d.naoAvaliado > 0
          ? 'o motor nao concluiu, atingindo ' + inteiro(execucao.itensComAvaliacaoNaoConcluida)
            + ' item(ns) distinto(s)'
          : 'toda avaliacao concluiu nesta rodada',
        endereco('naoAvaliados', execucao.id)),
      cartaoDeConforme(d.conforme),
    ]),
    barraDeDesfechos({ achado: d.achado, naoAvaliado: d.naoAvaliado, conforme }),
    el('p', {
      classe: 'nota',
      texto: 'Total de avaliacoes: ' + inteiro(d.avaliacoesProduzidas) + ' - ' + d.comoFoiObtido
        + '. Nenhum dos tres desfechos se deduz dos outros dois: o banco nao grava avaliacao '
        + 'conforme, e por isso ela vem derivada, com a conta ao lado.',
    }),

    el('h3', { texto: 'por regra' }),
    barrasPorRegra(execucao.porRegra),
    legenda(),
    tabelaPorRegra(execucao),
    el('h3', { texto: 'motivos de nao conclusao' }),
    motivos(execucao),
  ]);
}

function cartaoDeDesfecho(classe, rotulo, valor, explicacao, destino) {
  return el('div', { classe: 'desfecho ' + classe }, [
    el('div', { classe: 'rotulo', texto: rotulo }),
    el('div', { classe: 'numero', texto: inteiro(valor) }),
    el('p', { classe: 'explicacao', texto: explicacao }),
    destino ? el('a', { classe: 'botao', href: destino, texto: 'ver linha a linha' }) : null,
  ]);
}

/**
 * O conforme, sempre com a conta impressa - ou sem numero nenhum.
 *
 * Quando a derivacao nao fecha, o cartao escreve "nao derivavel" e o motivo, e
 * NAO escreve zero: zero afirmaria que nenhum item esta conforme, o que e uma
 * afirmacao sobre o acervo, quando o problema esta nos dados gravados.
 */
function cartaoDeConforme(contagem) {
  return el('div', { classe: 'desfecho d-conforme' }, [
    el('div', { classe: 'rotulo', texto: 'conformes' }),
    contagem.valor === null
      ? el('div', { classe: 'numero ausente', texto: 'nao derivavel' })
      : el('div', { classe: 'numero', texto: inteiro(contagem.valor) }),
    contagem.derivacao ? el('p', { classe: 'procedencia', texto: contagem.derivacao }) : null,
    contagem.motivoDaAusencia
      ? el('p', { classe: 'explicacao', texto: contagem.motivoDaAusencia }) : null,
  ]);
}

function legenda() {
  const item = (classe, texto) => el('span', {}, [
    el('i', { classe, style: 'background:var(--' + classe + ')' }),
    texto,
  ]);
  return el('div', { classe: 'legenda' }, [
    item('achado', 'apontamento (hachura a 45 graus)'),
    item('naoavaliado', 'nao avaliado (hachura a 135 graus)'),
    item('conforme', 'conforme (pontilhado)'),
  ]);
}

/**
 * Uma linha por regra aplicada, inclusive as que nao apontaram nada.
 *
 * A resposta ja traz todas: regra que rodou e nada encontrou nao some do
 * relatorio. A coluna "situacao" e o selo que separa os dois zeros.
 */
function tabelaPorRegra(execucao) {
  const linhas = execucao.porRegra.map((regra) => {
    const avaliouTudo = regra.naoAvaliado === 0;
    const selo = el('span', {
      classe: 'selo ' + (avaliouTudo ? 'selo-limpo' : 'selo-incompleto'),
      texto: avaliouTudo
        ? 'avaliou tudo'
        : 'nem tudo foi avaliado',
    });

    return el('tr', {}, [
      el('td', {}, [rotuloDaRegra(regra)]),
      el('td', { classe: 'num' }, [
        el('a', {
          href: endereco('achados', execucao.id, { regra: regra.regraId }),
          texto: inteiro(regra.achado),
        }),
      ]),
      el('td', { classe: 'num' }, [
        el('a', {
          href: endereco('naoAvaliados', execucao.id, { regra: regra.regraId }),
          texto: inteiro(regra.naoAvaliado),
        }),
      ]),
      el('td', { classe: 'num' }, [
        regra.conforme.valor === null
          ? el('span', { classe: 'ausente', texto: 'nao derivavel' })
          : document.createTextNode(inteiro(regra.conforme.valor)),
      ]),
      el('td', {}, [selo]),
      el('td', {
        classe: 'nota',
        texto: regra.achado === 0 && regra.naoAvaliado > 0
          ? 'zero apontamentos NAO quer dizer conformidade aqui: ' + inteiro(regra.naoAvaliado)
            + ' avaliacao(oes) desta regra nao concluiram'
          : (regra.achado === 0 ? 'a regra rodou por inteiro e nada encontrou' : ''),
      }),
    ]);
  });

  return el('div', { classe: 'rolagem' }, [
    el('table', {}, [
      el('caption', {
        texto: 'Toda regra aplicada tem linha, inclusive a que apontou zero. As tres colunas de '
          + 'contagem sao independentes: nenhuma sai por subtracao das outras.',
      }),
      el('thead', {}, [
        el('tr', {}, [
          el('th', { texto: 'regra' }),
          el('th', { classe: 'num', texto: 'apontamentos' }),
          el('th', { classe: 'num', texto: 'nao avaliados' }),
          el('th', { classe: 'num', texto: 'conformes (derivado)' }),
          el('th', { texto: 'situacao' }),
          el('th', { texto: 'leitura' }),
        ]),
      ]),
      el('tbody', {}, linhas),
    ]),
  ]);
}

/** Os motivos que as regras escreveram ao desistir, com quantas vezes cada um. */
function motivos(execucao) {
  const comMotivo = execucao.porRegra.filter((regra) => regra.motivosDoNaoAvaliado.length > 0);

  if (comMotivo.length === 0) {
    return el('p', {
      classe: 'tabela-vazia',
      texto: 'Nenhuma avaliacao ficou por concluir nesta rodada: as sete regras responderam '
        + 'sobre todos os itens.',
    });
  }

  const linhas = [];
  for (const regra of comMotivo) {
    for (const motivo of regra.motivosDoNaoAvaliado) {
      linhas.push(el('tr', {}, [
        el('td', {}, [
          el('a', {
            href: endereco('naoAvaliados', execucao.id, { regra: regra.regraId }),
          }, [rotuloDaRegra(regra)]),
        ]),
        el('td', { classe: 'num', texto: inteiro(motivo.quantidade) }),
        el('td', { texto: motivo.motivo }),
      ]));
    }
  }
  linhas.sort((a, b) => Number(b.children[1].textContent.replace(/\D/g, ''))
    - Number(a.children[1].textContent.replace(/\D/g, '')));

  return el('div', { classe: 'rolagem' }, [
    el('table', {}, [
      el('caption', {
        texto: 'O texto e o que a propria regra escreveu ao desistir. Ele diz se faltou campo no '
          + 'documento, se faltou tabela no catalogo, ou se a data ficou fora da cobertura '
          + 'declarada da carga - tres problemas de donos diferentes.',
      }),
      el('thead', {}, [
        el('tr', {}, [
          el('th', { texto: 'regra' }),
          el('th', { classe: 'num', texto: 'ocorrencias' }),
          el('th', { texto: 'motivo' }),
        ]),
      ]),
      el('tbody', {}, linhas),
    ]),
  ]);
}
