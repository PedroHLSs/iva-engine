import { el, trocar } from '../dom.js';
import { dataHora, inteiro } from '../formato.js';
import { faixaDeNatureza, identificacao, painelDaPlanilha, falha } from '../comum.js';
import { endereco } from '../roteador.js';
import { barraPlana, botaoDeInformacao } from '../painel.js';
import * as api from '../api.js';

const LIMITES = [25, 50, 100, 200];

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
    id: 'quantas-mostrar',
    aoMudar: (evento) => {
      window.location.hash = endereco('execucoes', null, { limite: evento.target.value });
    },
  }, LIMITES.map((valor) => el('option', {
    value: valor, texto: String(valor), selected: valor === limite ? 'selected' : null,
  })));

  const corpo = el('div', {});
  trocar(tela, [
    el('div', { classe: 'cabecalho-da-pagina' }, [
      el('div', {}, [
        el('h1', { texto: 'Execucoes' }),
        el('p', {
          classe: 'sub',
          texto: 'Da mais recente para a mais antiga, com catalogo, conjunto de regras e hash da '
            + 'entrada de cada rodada, para comparar duas sem abrir as duas.',
        }),
      ]),
      el('div', { classe: 'filtros barra-de-ferramentas' }, [
        el('div', { classe: 'filtro' }, [
          el('label', { for: 'quantas-mostrar', texto: 'quantas mostrar' }),
          seletor,
        ]),
        el('p', {
          classe: 'contagem-do-recorte',
          texto: 'mostrando ' + resposta.quantidade + ' de um recorte de ate ' + resposta.limite
            + '. Se as duas contagens forem iguais, pode haver rodadas mais antigas fora da lista.',
        }),
      ]),
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

  const no = el('section', { classe: 'cartao cartao-execucao' }, [
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
    faixaDeNatureza(execucao.natureza),
    identificacao(execucao),
    desfechos,
    el('h3', { texto: 'apontamentos por severidade' }),
    pilulasDeSeveridade(execucao.achadosPorSeveridade),
  ]);

  return { no, desfechos };
}

function pilulasDeSeveridade(porSeveridade) {
  const entradas = Object.entries(porSeveridade || {});
  if (entradas.length === 0) {
    return el('p', { classe: 'nota', texto: 'nenhum apontamento nesta rodada' });
  }
  return el('ul', { classe: 'pilulas', 'aria-label': 'apontamentos por severidade' },
    entradas.map(([nome, quantidade]) => el('li', { classe: 'pilula pilula-' + nome }, [
      el('span', { classe: 'pilula-nome', texto: nome }),
      el('span', { classe: 'pilula-numero', texto: inteiro(quantidade) }),
    ])));
}

function desenharDesfechos(detalhe) {
  const d = detalhe.desfechos;
  const conforme = d.conforme.valor === null ? null : Number(d.conforme.valor);

  const indicador = (classe, rotulo, valor, explicacao, motivoAVista) => {
    const info = explicacao ? botaoDeInformacao('Detalhe de ' + rotulo, explicacao) : null;
    return el('div', { classe: 'desfecho ' + classe }, [
      el('div', { classe: 'rotulo' }, [rotulo, info ? info.botao : null]),
      valor === null
        ? el('div', { classe: 'numero ausente', texto: 'nao derivavel' })
        : el('div', { classe: 'numero', texto: inteiro(valor) }),
      motivoAVista ? el('p', { classe: 'explicacao', texto: motivoAVista }) : null,
      info ? info.balao : null,
    ]);
  };

  return [
    el('div', { classe: 'desfechos' }, [
      indicador('d-achado', 'apontamentos', d.achado, null, null),
      indicador('d-naoavaliado', 'nao avaliados', d.naoAvaliado,
        d.naoAvaliado > 0
          ? 'atingindo ' + inteiro(detalhe.itensComAvaliacaoNaoConcluida) + ' item(ns) distinto(s)'
          : 'toda avaliacao concluiu nesta rodada',
        null),
      indicador('d-conforme', 'conformes', conforme, d.conforme.derivacao, d.conforme.motivoDaAusencia),
    ]),
    barraPlana({ achado: d.achado, naoAvaliado: d.naoAvaliado, conforme }),
    el('p', {
      classe: 'nota nota-da-barra',
      texto: 'Total de avaliacoes: ' + inteiro(d.avaliacoesProduzidas) + ' - ' + d.comoFoiObtido,
    }),
  ];
}
