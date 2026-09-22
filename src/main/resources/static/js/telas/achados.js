/* ---------------------------------------------------------------------------
   Tela 3 - Achados.

   ORDEM PADRAO E VALOR EM RISCO, NAO SEVERIDADE

   Tres ocorrencias somando muito vem antes de oitocentas somando pouco. A
   severidade continua visivel em toda linha e continua sendo criterio de
   ordenacao a um clique, mas nao e o padrao: quem recebe o lote precisa saber
   o que corrigir primeiro, e "primeiro" e uma pergunta sobre dinheiro.

   POR QUE TUDO E CARREGADO ANTES DE ORDENAR

   Ordenar por valor e agrupar por cadastro sao operacoes globais. Feitas sobre
   uma pagina, dariam um resultado que parece certo e esta errado. Entao a tela
   busca todas as paginas antes de desenhar, mostrando o progresso.

   GRUPO SEM VALOR CALCULAVEL NAO VAI PARA O FIM DA LISTA

   Ele sai numa secao propria, com titulo proprio. Ausencia de valor nao e valor
   zero, e o rodape de uma lista ordenada por dinheiro e exatamente o lugar onde
   se le "isso aqui nao vale nada".
   --------------------------------------------------------------------------- */

import { el, trocar } from '../dom.js';
import {
  inteiro, severidade, documentoCurto, data, ausente, rotuloDaRegra, regraEmTexto,
} from '../formato.js';
import { comoQuantia } from '../decimal.js';
import { navegacaoDaExecucao, painelDaPlanilha, falha } from '../comum.js';
import { endereco, enderecoDoAchado } from '../roteador.js';
import { agrupar, ordenarAchados, porGravidade, porOcorrencias } from '../agrupamento.js';
import * as api from '../api.js';

const SEVERIDADES = ['CRITICA', 'GRAVE', 'MODERADA', 'INFORMATIVA'];
const TRATATIVAS = ['ABERTO', 'ACEITO', 'REFUTADO'];
const ORDENS = [
  { codigo: 'valor', rotulo: 'valor em risco (maior primeiro)' },
  { codigo: 'severidade', rotulo: 'severidade (mais grave primeiro)' },
  { codigo: 'ocorrencias', rotulo: 'ocorrencias (mais repetido primeiro)' },
];

export async function desenhar(tela, parametros, rota) {
  const filtros = {
    regra: parametros.get('regra') || '',
    severidade: parametros.get('severidade') || '',
    status: parametros.get('status') || '',
  };
  const ordem = parametros.get('ordem') || 'valor';
  const agrupado = parametros.get('agrupar') !== 'nao';

  const progresso = el('p', { classe: 'carregando', texto: 'Lendo os apontamentos...' });
  trocar(tela, [
    el('a', { classe: 'voltar', href: endereco('panorama', rota.execucaoId), texto: 'Panorama' }),
    el('h1', { texto: 'Achados' }),
    progresso,
  ]);

  let execucao;
  let carregado;
  try {
    execucao = await api.execucao(rota.execucaoId);
    carregado = await api.todasAsPaginas(
      (pagina, tamanho) => api.paginaDeAchados(rota.execucaoId, filtros, pagina, tamanho),
      (feitas, total, trazidas, universo) => {
        progresso.textContent = 'Lendo pagina ' + feitas + ' de ' + total + ' - '
          + inteiro(trazidas) + ' de ' + inteiro(universo) + ' apontamento(s).';
      },
    );
  } catch (erro) {
    trocar(tela, [
      el('a', { classe: 'voltar', href: endereco('panorama', rota.execucaoId), texto: 'Panorama' }),
      el('h1', { texto: 'Achados' }),
      falha(erro),
    ]);
    return;
  }

  const planilha = painelDaPlanilha(execucao.id);
  const corpo = el('div', {});

  trocar(tela, [
    el('a', { classe: 'voltar', href: endereco('panorama', rota.execucaoId), texto: 'Panorama' }),
    el('div', { classe: 'cartao-titulo' }, [
      el('h1', { texto: 'Achados' }),
      el('div', { classe: 'acoes' }, [planilha.botao]),
    ]),
    planilha.painel,
    navegacaoDaExecucao(execucao.id, 'achados'),
    barraDeFiltros(rota.execucaoId, filtros, ordem, agrupado, execucao),
    resumoDoRecorte(carregado, execucao),
    corpo,
  ]);

  desenharConteudo(corpo, execucao, carregado.linhas, ordem, agrupado);
}

function irComFiltro(execucaoId, filtros, ordem, agrupado, mudanca) {
  const atual = Object.assign({}, filtros, { ordem, agrupar: agrupado ? '' : 'nao' }, mudanca);
  window.location.hash = endereco('achados', execucaoId, atual);
}

function barraDeFiltros(execucaoId, filtros, ordem, agrupado, execucao) {
  // Opcao e texto simples, ou { valor, texto } quando o que se mostra nao e o que
  // vai no endereco - caso da regra, que mostra o nome e filtra pelo codigo.
  const escolha = (rotulo, chave, opcoes, atual) => {
    const seletor = el('select', {
      aoMudar: (evento) => irComFiltro(execucaoId, filtros, ordem, agrupado,
        { [chave]: evento.target.value }),
    }, [el('option', { value: '', texto: 'todas' })].concat(opcoes.map((opcao) => {
      const valor = typeof opcao === 'string' ? opcao : opcao.valor;
      return el('option', {
        value: valor,
        texto: typeof opcao === 'string' ? opcao : opcao.texto,
        selected: valor === atual ? 'selected' : null,
      });
    })));
    return el('div', { classe: 'filtro' }, [el('label', { texto: rotulo }), seletor]);
  };

  const regras = execucao.porRegra.map((regra) => ({
    valor: regra.regraId,
    texto: regraEmTexto(regra),
  }));

  const ordenacao = el('select', {
    aoMudar: (evento) => irComFiltro(execucaoId, filtros, evento.target.value, agrupado, {}),
  }, ORDENS.map((opcao) => el('option', {
    value: opcao.codigo, texto: opcao.rotulo,
    selected: opcao.codigo === ordem ? 'selected' : null,
  })));

  const caixa = el('input', {
    type: 'checkbox', id: 'agrupar', checked: agrupado ? 'checked' : null,
    aoMudar: (evento) => irComFiltro(execucaoId, filtros, ordem, evento.target.checked, {}),
  });

  return el('div', { classe: 'filtros' }, [
    escolha('regra', 'regra', regras, filtros.regra),
    escolha('severidade', 'severidade', SEVERIDADES, filtros.severidade),
    escolha('tratativa', 'status', TRATATIVAS, filtros.status),
    el('div', { classe: 'filtro' }, [el('label', { texto: 'ordem' }), ordenacao]),
    el('div', { classe: 'filtro-caixa' }, [
      caixa,
      el('label', { for: 'agrupar', texto: 'agrupar por NCM + cClassTrib + regra' }),
    ]),
  ]);
}

/**
 * Quantos apontamentos entraram, e quantos existem.
 *
 * Filtrar nao e esconder: o total antes do recorte vem escrito, para que uma
 * listagem curta nao passe por acervo limpo, e o filtro aplicado vem escrito,
 * para que ninguem confunda "nada atende" com "ninguem procurou".
 */
function resumoDoRecorte(carregado, execucao) {
  const f = carregado.filtro;
  const criterios = [];
  if (f.regraId) {
    // O filtro volta so com o codigo; o nome sai da linha por regra desta
    // execucao. Codigo que a execucao nao tem fica como veio.
    const daExecucao = execucao.porRegra.find((regra) => regra.regraId === f.regraId);
    criterios.push('regra ' + (daExecucao ? regraEmTexto(daExecucao) : f.regraId));
  }
  if (f.severidade) {
    criterios.push('severidade ' + f.severidade);
  }
  if (f.statusDeTratativa) {
    criterios.push('tratativa ' + f.statusDeTratativa);
  }

  return el('div', {}, [
    el('p', { classe: 'contagem-do-recorte' }, [
      el('strong', { texto: inteiro(carregado.linhas.length) }),
      ' apontamento(s) carregado(s) de ' + inteiro(carregado.pagina.totalDeElementos)
        + ' que atendem ao filtro. A execucao inteira tem '
        + inteiro(execucao.desfechos.achado) + '.',
    ]),
    el('p', {
      classe: 'nota',
      texto: criterios.length === 0
        ? 'Nenhum filtro aplicado: esta e a lista inteira desta execucao.'
        : 'Filtro aplicado: ' + criterios.join(', ') + '. As contagens abaixo sao do recorte, '
          + 'nao do acervo.',
    }),
    execucao.desfechos.naoAvaliado > 0
      ? el('div', { classe: 'aviso' }, [
        el('strong', { texto: 'Esta lista nao cobre a rodada inteira. ' }),
        inteiro(execucao.desfechos.naoAvaliado) + ' avaliacao(oes) nao concluiram e nao estao '
          + 'aqui: nao sao apontamento e nao sao conformidade. ',
        el('a', {
          href: endereco('naoAvaliados', execucao.id),
          texto: 'Ver o que ficou sem avaliacao.',
        }),
      ])
      : null,
  ]);
}

function desenharConteudo(corpo, execucao, achados, ordem, agrupado) {
  if (achados.length === 0) {
    trocar(corpo, [el('p', {
      classe: 'tabela-vazia',
      texto: 'Nenhum apontamento atende a este filtro. Isso nao quer dizer que o lote esteja '
        + 'limpo: veja o painel acima e a tela de nao avaliados.',
    })]);
    return;
  }

  if (!agrupado) {
    const soltos = ordenarAchados(achados);
    trocar(corpo, [
      faixa('Ordenados por valor em risco', inteiro(soltos.comValor.length)
        + ' apontamento(s) com valor calculavel', false),
      tabelaDeOcorrencias(execucao.id, soltos.comValor),
      soltos.semValor.length > 0 ? faixa('Sem valor em risco calculavel',
        inteiro(soltos.semValor.length) + ' apontamento(s) - ausencia de valor nao e valor zero, '
        + 'entao eles nao entram na ordenacao por dinheiro', true) : null,
      soltos.semValor.length > 0 ? tabelaDeOcorrencias(execucao.id, soltos.semValor) : null,
    ]);
    return;
  }

  const grupos = agrupar(achados);
  const comValor = ordem === 'severidade' ? porGravidade(grupos.comValor)
    : (ordem === 'ocorrencias' ? porOcorrencias(grupos.comValor) : grupos.comValor);
  const semValor = ordem === 'ocorrencias' ? porOcorrencias(grupos.semValor) : grupos.semValor;

  trocar(corpo, [
    el('p', {
      classe: 'contagem-do-recorte',
      texto: inteiro(achados.length) + ' apontamento(s) em ' + inteiro(grupos.total)
        + ' grupo(s). Erro de parametrizacao e sistematico: o que se corrige e o cadastro, '
        + 'nao cada nota.',
    }),
    faixa('Grupos ordenados por valor em risco', inteiro(comValor.length) + ' grupo(s)', false),
    el('div', {}, comValor.map((grupo) => desenharGrupo(execucao.id, grupo))),
    semValor.length > 0
      ? faixa('Grupos sem valor em risco calculavel', inteiro(semValor.length)
        + ' grupo(s) - a regra apontou sem quantia a opor. Nao sao zero, e por isso nao entram '
        + 'na ordenacao acima; aqui a ordem e por gravidade', true)
      : null,
    el('div', {}, semValor.map((grupo) => desenharGrupo(execucao.id, grupo))),
  ]);
}

function faixa(titulo, explicacao, semValor) {
  return el('div', { classe: 'faixa-secao' + (semValor ? ' sem-valor' : '') }, [
    el('h3', { texto: titulo }),
    el('p', { classe: 'explicacao', texto: explicacao }),
  ]);
}

/**
 * Um grupo, com o nivel de agrupamento escrito no proprio cartao.
 *
 * O selo do nivel nao e enfeite: sem ele, um grupo de R05 - que nao expoe NCM
 * nem cClassTrib e por isso so pode ser agrupado por regra - teria a mesma
 * aparencia de um grupo de R03 agrupado pela chave inteira, e as duas coisas
 * dizem coisas diferentes sobre o que corrigir. Quando algum componente falta,
 * o proprio motivo observado vai escrito ao lado.
 */
function desenharGrupo(execucaoId, grupo) {
  const componente = (rotulo, valor, motivo) => el('span', { classe: 'campo' }, [
    rotulo + ' ',
    valor !== null ? el('b', { texto: valor }) : ausente(motivo),
  ]);

  const tratativas = Array.from(grupo.porTratativa.entries())
    .map(([status, quantidade]) => status + ' ' + quantidade)
    .join(', ');

  const medida = el('div', { classe: 'grupo-medida' }, [
    grupo.valorSomado !== null
      ? el('div', { classe: 'valor', texto: comoQuantia(grupo.valorSomado) })
      : el('div', { classe: 'valor ausente', texto: 'sem valor calculavel' }),
    el('div', {
      classe: 'ocorrencias',
      texto: inteiro(grupo.ocorrencias) + ' ocorrencia(s) em '
        + inteiro(grupo.documentos.size) + ' documento(s)',
    }),
  ]);

  const resumo = el('summary', {}, [
    el('div', { classe: 'grupo-chave' }, [
      rotuloDaRegra(grupo),
      severidade(grupo.severidade),
      componente('NCM', grupo.ncm, grupo.motivoDoNcmAusente),
      componente('cClassTrib', grupo.cClassTrib, grupo.motivoDoCClassTribAusente),
    ]),
    medida,
    el('div', { classe: 'grupo-nivel' }, [
      el('span', {
        classe: 'selo-nivel' + (grupo.nivelDegradado ? ' degradado' : ''),
        texto: grupo.rotuloDoNivel,
      }),
      el('span', { texto: 'tratativa: ' + tratativas }),
    ]),
  ]);

  const corpo = el('div', { classe: 'grupo-corpo' }, [
    grupo.derivacaoDaSoma
      ? el('p', { classe: 'nota', texto: 'valor em risco: ' + grupo.derivacaoDaSoma })
      : null,
    grupo.observacaoDaSomaParcial
      ? el('div', { classe: 'aviso', texto: grupo.observacaoDaSomaParcial })
      : null,
    grupo.motivoDoValorAusente
      ? el('div', { classe: 'aviso', texto: grupo.motivoDoValorAusente })
      : null,
    grupo.motivoDoNcmAusente
      ? el('p', { classe: 'nota', texto: 'NCM: ' + grupo.motivoDoNcmAusente })
      : null,
    grupo.motivoDoCClassTribAusente
      ? el('p', { classe: 'nota', texto: 'cClassTrib: ' + grupo.motivoDoCClassTribAusente })
      : null,
    tabelaDeOcorrencias(execucaoId, grupo.achados),
  ]);

  return el('details', { classe: 'grupo' }, [resumo, corpo]);
}

/** As ocorrencias, uma linha por apontamento, sem identificador em texto claro. */
function tabelaDeOcorrencias(execucaoId, achados) {
  const linhas = achados.map((achado) => el('tr', {}, [
    el('td', { classe: 'mono', texto: achado.documento.pseudonimo.slice(0, 12) }),
    el('td', { texto: documentoCurto(achado.documento) }),
    el('td', { texto: data(achado.documento.dataEmissao) }),
    el('td', { texto: achado.documento.ufEmitente || '' }),
    el('td', { classe: 'num', texto: String(achado.numeroItem) }),
    el('td', {}, [rotuloDaRegra(achado)]),
    el('td', {}, [severidade(achado.severidade)]),
    el('td', { classe: 'num' }, [
      achado.valorEmRisco !== null
        ? document.createTextNode(comoQuantia(achado.valorEmRisco))
        : ausente(achado.motivoDoValorAusente),
    ]),
    el('td', { texto: achado.statusDeTratativa }),
    el('td', {}, [
      el('a', { href: enderecoDoAchado(execucaoId, achado.id), texto: 'evidencia' }),
    ]),
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
          el('th', { texto: 'severidade' }),
          el('th', { classe: 'num', texto: 'valor em risco' }),
          el('th', { texto: 'tratativa' }),
          el('th', { texto: '' }),
        ]),
      ]),
      el('tbody', {}, linhas),
    ]),
  ]);
}
