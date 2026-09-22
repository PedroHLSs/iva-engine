/* ---------------------------------------------------------------------------
   Tela 2b - Nao avaliados, linha a linha.

   Existe porque o panorama mostra o agregado, e clicar em "118 nao avaliados"
   precisa levar a algum lugar. Sem esta tela, NAO_AVALIADO seria uma categoria
   que a interface conta mas nao deixa examinar - e a regra 1 viraria slogan.

   As linhas nao sao deduplicadas entre execucoes, e nao deveriam ser: nao
   concluir e fato da rodada, nao do documento. A mesma regra sobre o mesmo item
   pode nao concluir hoje por falta de tabela e concluir amanha.
   --------------------------------------------------------------------------- */

import { el, trocar } from '../dom.js';
import {
  inteiro, data, documentoCurto, chipDesfecho, rotuloDaRegra, regraEmTexto,
} from '../formato.js';
import { navegacaoDaExecucao, painelDaPlanilha, falha } from '../comum.js';
import { endereco } from '../roteador.js';
import * as api from '../api.js';

export async function desenhar(tela, parametros, rota) {
  const filtros = { regra: parametros.get('regra') || '' };

  const voltar = el('a', {
    classe: 'voltar', href: endereco('panorama', rota.execucaoId), texto: 'Panorama',
  });
  const progresso = el('p', { classe: 'carregando', texto: 'Lendo as avaliacoes nao concluidas...' });
  trocar(tela, [voltar, el('h1', { texto: 'Nao avaliados' }), progresso]);

  let execucao;
  let carregado;
  try {
    execucao = await api.execucao(rota.execucaoId);
    carregado = await api.todasAsPaginas(
      (pagina, tamanho) => api.paginaDeNaoAvaliados(rota.execucaoId, filtros, pagina, tamanho),
      (feitas, total, trazidas, universo) => {
        progresso.textContent = 'Lendo pagina ' + feitas + ' de ' + total + ' - '
          + inteiro(trazidas) + ' de ' + inteiro(universo) + ' linha(s).';
      },
    );
  } catch (erro) {
    trocar(tela, [voltar, el('h1', { texto: 'Nao avaliados' }), falha(erro)]);
    return;
  }

  const planilha = painelDaPlanilha(execucao.id);
  const seletor = el('select', {
    aoMudar: (evento) => {
      window.location.hash = endereco('naoAvaliados', rota.execucaoId,
        { regra: evento.target.value });
    },
  }, [el('option', { value: '', texto: 'todas' })].concat(execucao.porRegra.map((regra) => el('option', {
    value: regra.regraId,
    texto: regraEmTexto(regra),
    selected: regra.regraId === filtros.regra ? 'selected' : null,
  }))));

  trocar(tela, [
    voltar,
    el('div', { classe: 'cartao-titulo' }, [
      el('h1', {}, [chipDesfecho('NAO_AVALIADO', 'NAO AVALIADO'), ' o que o motor nao julgou']),
      el('div', { classe: 'acoes' }, [planilha.botao]),
    ]),
    planilha.painel,
    navegacaoDaExecucao(execucao.id, 'naoAvaliados'),
    el('p', {
      classe: 'sub',
      texto: 'Nao e apontamento e nao e conformidade: e a terceira coisa. Cada linha diz o que '
        + 'faltou - campo no documento, tabela no catalogo, ou cobertura da carga - porque sao '
        + 'tres problemas de donos diferentes.',
    }),
    el('div', { classe: 'filtros' }, [
      el('div', { classe: 'filtro' }, [el('label', { texto: 'regra' }), seletor]),
    ]),
    el('p', { classe: 'contagem-do-recorte' }, [
      el('strong', { texto: inteiro(carregado.linhas.length) }),
      ' linha(s) carregada(s) de ' + inteiro(carregado.pagina.totalDeElementos)
        + ' que atendem ao filtro. A execucao inteira tem '
        + inteiro(execucao.desfechos.naoAvaliado) + ', atingindo '
        + inteiro(execucao.itensComAvaliacaoNaoConcluida) + ' item(ns) distinto(s).',
    ]),
    porMotivo(carregado.linhas),
  ]);
}

/** Agrupa por (regra, motivo): o que se corrige e a causa, nao a linha. */
function porMotivo(linhas) {
  if (linhas.length === 0) {
    return el('p', {
      classe: 'tabela-vazia',
      texto: 'Nenhuma avaliacao ficou por concluir neste recorte.',
    });
  }

  const grupos = new Map();
  for (const linha of linhas) {
    const chave = JSON.stringify([linha.regraId, linha.motivo]);
    const grupo = grupos.get(chave) || {
      regraId: linha.regraId,
      regraNome: linha.regraNome || null,
      motivoDoNomeDaRegraAusente: linha.motivoDoNomeDaRegraAusente || null,
      motivo: linha.motivo,
      linhas: [],
    };
    grupo.linhas.push(linha);
    grupos.set(chave, grupo);
  }

  const ordenados = Array.from(grupos.values())
    .sort((a, b) => b.linhas.length - a.linhas.length);

  return el('div', {}, ordenados.map((grupo) => el('details', { classe: 'grupo' }, [
    el('summary', {}, [
      el('div', { classe: 'grupo-chave' }, [
        rotuloDaRegra(grupo),
        el('span', { classe: 'campo', texto: grupo.motivo }),
      ]),
      el('div', { classe: 'grupo-medida' }, [
        el('div', { classe: 'valor', texto: inteiro(grupo.linhas.length) }),
        el('div', { classe: 'ocorrencias', texto: 'ocorrencia(s)' }),
      ]),
    ]),
    el('div', { classe: 'grupo-corpo' }, [tabela(grupo.linhas)]),
  ])));
}

function tabela(linhas) {
  const corpo = linhas.map((linha) => el('tr', {}, [
    el('td', { classe: 'mono', texto: linha.documento.pseudonimo.slice(0, 12) }),
    el('td', { texto: documentoCurto(linha.documento) }),
    el('td', { texto: data(linha.documento.dataEmissao) }),
    el('td', { texto: linha.documento.ufEmitente || '' }),
    el('td', { classe: 'num', texto: String(linha.numeroItem) }),
    el('td', {}, [
      rotuloDaRegra(linha),
      el('span', { classe: 'regra-codigo', texto: ' versao ' + linha.regraVersao }),
    ]),
    el('td', {}, [chipDesfecho('NAO_AVALIADO', linha.resultado)]),
  ]));

  return el('div', { classe: 'rolagem' }, [
    el('table', {}, [
      el('thead', {}, [
        el('tr', {}, [
          el('th', { texto: 'pseudonimo' }),
          el('th', { texto: 'modelo/serie/numero' }),
          el('th', { texto: 'emissao' }),
          el('th', { texto: 'UF' }),
          el('th', { classe: 'num', texto: 'item' }),
          el('th', { texto: 'regra' }),
          el('th', { texto: 'resultado' }),
        ]),
      ]),
      el('tbody', {}, corpo),
    ]),
  ]);
}
