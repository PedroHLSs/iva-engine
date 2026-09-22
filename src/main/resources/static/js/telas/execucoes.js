/* ---------------------------------------------------------------------------
   Tela 1 - Execucoes.

   POR QUE CADA CARTAO FAZ UMA SEGUNDA CHAMADA

   GET /api/execucoes traz a identificacao da rodada e o total de apontamentos,
   mas NAO traz nao avaliados nem conformes. Uma lista montada so com isso
   escreveria "43 apontamentos" e pararia ali - e uma execucao com 4
   apontamentos e 7 nao avaliados apareceria como lote quase limpo, que e
   exatamente o que esta interface existe para nao deixar acontecer.

   Entao cada linha busca o detalhe da propria execucao, que tem os tres
   desfechos. Enquanto o detalhe nao chega, o cartao escreve "lendo..."; se
   falhar, escreve que nao foi possivel ler. Em nenhum dos dois casos ele mostra
   zero, e em nenhum dos dois ele some com a categoria.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../dom.js';
import { dataHora, inteiro, severidade } from '../formato.js';
import { identificacao, painelDaPlanilha, falha } from '../comum.js';
import { endereco } from '../roteador.js';
import { barraDeDesfechos } from '../svg.js';
import * as api from '../api.js';

const LIMITES = [25, 50, 100, 200];

/** Busca os detalhes com paralelismo limitado, para nao abrir 200 conexoes. */
async function comDetalhes(execucoes, aoChegar) {
  const fila = execucoes.slice();
  const trabalhadores = new Array(Math.min(4, fila.length)).fill(null).map(async () => {
    while (fila.length > 0) {
      const execucao = fila.shift();
      try {
        aoChegar(execucao.id, await api.execucao(execucao.id), null);
      } catch (erro) {
        aoChegar(execucao.id, null, erro);
      }
    }
  });
  await Promise.all(trabalhadores);
}

export async function desenhar(tela, parametros) {
  const limite = Number(parametros.get('limite')) || LIMITES[0];

  trocar(tela, [
    el('h1', { texto: 'Execucoes' }),
    el('p', { classe: 'carregando', texto: 'Lendo as execucoes gravadas...' }),
  ]);

  let resposta;
  try {
    resposta = await api.execucoes(limite);
  } catch (erro) {
    trocar(tela, [el('h1', { texto: 'Execucoes' }), falha(erro)]);
    return;
  }

  const seletor = el('select', {
    aoMudar: (evento) => {
      window.location.hash = endereco('execucoes', null, { limite: evento.target.value });
    },
  }, LIMITES.map((valor) => el('option', {
    value: valor, texto: String(valor), selected: valor === limite ? 'selected' : null,
  })));

  const corpo = el('div', {});
  trocar(tela, [
    el('h1', { texto: 'Execucoes' }),
    el('p', {
      classe: 'sub',
      texto: 'Da mais recente para a mais antiga. Cada rodada aparece com a identificacao '
        + 'inteira - catalogo, conjunto de regras e hash da entrada - porque e o que permite '
        + 'comparar duas rodadas sem abrir as duas.',
    }),
    el('div', { classe: 'filtros' }, [
      el('div', { classe: 'filtro' }, [
        el('label', { texto: 'quantas mostrar' }),
        seletor,
      ]),
      el('p', {
        classe: 'contagem-do-recorte',
        texto: 'mostrando ' + resposta.quantidade + ' de um recorte de ate ' + resposta.limite
          + '. Se as duas contagens forem iguais, pode haver rodadas mais antigas fora da lista.',
      }),
    ]),
    corpo,
  ]);

  if (resposta.quantidade === 0) {
    corpo.appendChild(el('p', {
      classe: 'tabela-vazia',
      texto: 'Nenhuma auditoria foi gravada ainda. Rode o comando "auditar" na CLI.',
    }));
    return;
  }

  const desfechosPorId = new Map();
  for (const execucao of resposta.execucoes) {
    const cartao = desenharCartao(execucao);
    desfechosPorId.set(execucao.id, cartao.desfechos);
    corpo.appendChild(cartao.no);
  }

  await comDetalhes(resposta.execucoes, (id, detalhe, erro) => {
    const alvo = desfechosPorId.get(id);
    if (alvo) {
      trocar(alvo, detalhe ? desenharDesfechos(detalhe) : [falha(erro)]);
    }
  });
}

function desenharCartao(execucao) {
  const planilha = painelDaPlanilha(execucao.id);

  const desfechos = el('div', {}, [
    el('p', { classe: 'carregando', texto: 'lendo os tres desfechos desta rodada...' }),
  ]);

  const porSeveridade = el('div', { classe: 'acoes' },
    Object.entries(execucao.achadosPorSeveridade).map(([nome, quantidade]) => el('span', {
      classe: 'chip chip-neutro',
    }, [severidade(nome), ' ' + inteiro(quantidade)])));

  const no = el('section', { classe: 'cartao' }, [
    el('div', { classe: 'cartao-titulo' }, [
      el('h2', { texto: dataHora(execucao.dataHora) }),
      el('div', { classe: 'acoes' }, [
        el('a', {
          classe: 'botao principal',
          href: endereco('panorama', execucao.id),
          texto: 'abrir',
        }),
        planilha.botao,
      ]),
    ]),
    planilha.painel,
    identificacao(execucao),
    desfechos,
    el('h3', { texto: 'apontamentos por severidade' }),
    porSeveridade,
  ]);

  return { no, desfechos };
}

/**
 * Os tres desfechos do cartao.
 *
 * Nao avaliado nunca aparece somado a conforme, e conforme nunca aparece sem a
 * conta que o produziu: ele nao esta gravado em coluna nenhuma, e numero sem
 * procedencia numa auditoria e numero que ninguem confere.
 */
function desenharDesfechos(detalhe) {
  const d = detalhe.desfechos;
  const conforme = d.conforme.valor === null ? null : Number(d.conforme.valor);

  const linha = (classe, rotulo, valor, procedencia, motivo) => el('div', {
    classe: 'desfecho ' + classe,
  }, [
    el('div', { classe: 'rotulo', texto: rotulo }),
    valor === null
      ? el('div', { classe: 'numero ausente', texto: 'nao derivavel' })
      : el('div', { classe: 'numero', texto: inteiro(valor) }),
    procedencia ? el('p', { classe: 'procedencia', texto: procedencia }) : null,
    motivo ? el('p', { classe: 'explicacao', texto: motivo }) : null,
  ]);

  return [
    el('div', { classe: 'desfechos' }, [
      linha('d-achado', 'apontamentos', d.achado, null, null),
      linha('d-naoavaliado', 'nao avaliados', d.naoAvaliado, null,
        d.naoAvaliado > 0
          ? 'atingindo ' + inteiro(detalhe.itensComAvaliacaoNaoConcluida) + ' item(ns) distinto(s)'
          : 'toda avaliacao concluiu nesta rodada'),
      linha('d-conforme', 'conformes', conforme, d.conforme.derivacao, d.conforme.motivoDaAusencia),
    ]),
    barraDeDesfechos({ achado: d.achado, naoAvaliado: d.naoAvaliado, conforme }),
    el('p', {
      classe: 'nota',
      texto: 'Total de avaliacoes: ' + inteiro(d.avaliacoesProduzidas) + ' - ' + d.comoFoiObtido,
    }),
  ];
}
