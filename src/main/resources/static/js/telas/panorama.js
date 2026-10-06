import { el, trocar } from '../dom.js';
import { dataHora, inteiro, rotuloDaRegra } from '../formato.js';
import { faixaDeNatureza, identificacao, navegacaoDaExecucao, painelDaPlanilha, falha } from '../comum.js';
import { endereco } from '../roteador.js';
import { barraPlana, botaoDeInformacao } from '../painel.js';
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

  const sobreOTotal = botaoDeInformacao('Como os tres desfechos foram contados', [
    el('p', { texto: 'Total de avaliacoes: ' + inteiro(d.avaliacoesProduzidas) + ' - ' + d.comoFoiObtido + '.' }),
    el('p', {
      texto: 'Nenhum dos tres desfechos se deduz dos outros dois: o banco nao grava avaliacao '
        + 'conforme, e por isso ela vem derivada, com a conta ao lado.',
    }),
  ]);
  const sobreOsMotivos = botaoDeInformacao('Sobre os motivos', el('p', {
    texto: 'O texto e o que a propria regra escreveu ao desistir. Ele diz se faltou campo no '
      + 'documento, se faltou tabela no catalogo, ou se a data ficou fora da cobertura '
      + 'declarada da carga - tres problemas de donos diferentes.',
  }));

  trocar(tela, [
    el('a', { classe: 'voltar', href: endereco('execucoes'), texto: 'Execucoes' }),
    el('div', { classe: 'cartao-titulo cabecalho-do-panorama' }, [
      el('h1', { texto: 'Panorama de ' + dataHora(execucao.dataHora) }),
      el('div', { classe: 'acoes' }, [planilha.botao]),
    ]),
    planilha.painel,
    faixaDeNatureza(execucao.natureza),
    navegacaoDaExecucao(execucao.id, 'panorama'),
    el('section', { classe: 'cartao cartao-painel' }, [identificacao(execucao)]),

    el('section', { classe: 'cartao cartao-painel' }, [
      tituloDeSecao('Os tres desfechos', sobreOTotal),
      el('div', { classe: 'desfechos' }, [
        indicador('d-achado', 'apontamentos', d.achado, null, null,
          endereco('achados', execucao.id)),
        indicador('d-naoavaliado', 'nao avaliados', d.naoAvaliado,
          d.naoAvaliado > 0
            ? 'O motor nao concluiu, atingindo ' + inteiro(execucao.itensComAvaliacaoNaoConcluida)
              + ' item(ns) distinto(s).'
            : 'Toda avaliacao concluiu nesta rodada.',
          null, endereco('naoAvaliados', execucao.id)),
        indicador('d-conforme', 'conformes', conforme, d.conforme.derivacao,
          d.conforme.motivoDaAusencia, null),
      ]),
      barraPlana({ achado: d.achado, naoAvaliado: d.naoAvaliado, conforme }),
    ]),

    el('section', { classe: 'cartao cartao-painel' }, [
      tituloDeSecao('Por regra', null),
      tabelaPorRegra(execucao),
    ]),

    el('section', { classe: 'cartao cartao-painel' }, [
      tituloDeSecao('Motivos de nao conclusao', sobreOsMotivos),
      motivos(execucao),
    ]),
  ]);
}

function tituloDeSecao(texto, info) {
  return el('div', { classe: 'titulo-de-secao' }, [
    el('div', { classe: 'titulo-de-secao-linha' }, [el('h2', { texto }), info ? info.botao : null]),
    info ? info.balao : null,
  ]);
}

function indicador(classe, rotulo, valor, explicacao, motivoAVista, destino) {
  const info = explicacao
    ? botaoDeInformacao('Detalhe de ' + rotulo, el('p', { texto: explicacao }))
    : null;
  return el('div', { classe: 'desfecho ' + classe }, [
    el('div', { classe: 'rotulo' }, [rotulo, info ? info.botao : null]),
    valor === null
      ? el('div', { classe: 'numero ausente', texto: 'nao derivavel' })
      : el('div', { classe: 'numero', texto: inteiro(valor) }),
    motivoAVista ? el('p', { classe: 'explicacao', texto: motivoAVista }) : null,
    info ? info.balao : null,
    destino ? el('a', { classe: 'ver-linhas', href: destino, texto: 'ver linha a linha →' }) : null,
  ]);
}

function tabelaPorRegra(execucao) {
  const linhas = execucao.porRegra.map((regra) => {
    const avaliouTudo = regra.naoAvaliado === 0;
    const situacao = el('span', {
      classe: 'situacao ' + (avaliouTudo ? 'situacao-completa' : 'situacao-incompleta'),
    }, [
      el('span', {
        classe: 'situacao-marca', 'aria-hidden': 'true', texto: avaliouTudo ? '●' : '▲',
      }),
      avaliouTudo ? 'Avaliou tudo' : 'Nem tudo foi avaliado',
    ]);

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
      el('td', {}, [situacao]),
    ]);
  });

  return el('div', { classe: 'rolagem' }, [
    el('table', { classe: 'tabela-limpa' }, [
      el('caption', { texto: 'Toda regra aplicada tem linha, inclusive a que apontou zero.' }),
      el('thead', {}, [
        el('tr', {}, [
          el('th', { scope: 'col', texto: 'Regra' }),
          el('th', { scope: 'col', classe: 'num', texto: 'Apontamentos' }),
          el('th', { scope: 'col', classe: 'num', texto: 'Nao avaliados' }),
          el('th', { scope: 'col', classe: 'num', texto: 'Conformes' }),
          el('th', { scope: 'col', texto: 'Situacao' }),
        ]),
      ]),
      el('tbody', {}, linhas),
    ]),
  ]);
}

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
    el('table', { classe: 'tabela-limpa' }, [
      el('thead', {}, [
        el('tr', {}, [
          el('th', { scope: 'col', texto: 'Regra' }),
          el('th', { scope: 'col', classe: 'num', texto: 'Ocorrencias' }),
          el('th', { scope: 'col', texto: 'Motivo' }),
        ]),
      ]),
      el('tbody', {}, linhas),
    ]),
  ]);
}
