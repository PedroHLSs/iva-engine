import { el, trocar } from '../../dom.js';
import { dataHora, inteiro } from '../../formato.js';
import { botaoDeInformacao } from '../../painel.js';
import * as api from '../api.js';
import { falha, selo, valorOuMotivo } from '../pecas.js';
import { enderecoDeEnvio, enderecoDoResultado, irPara } from '../roteador.js';

const TAMANHO = 20;

export async function desenhar(raiz, parametros) {
  const filtros = {
    de: parametros.get('de') || '',
    ate: parametros.get('ate') || '',
    situacaoMaisGrave: parametros.get('situacaoMaisGrave') || '',
    minimoDeDivergencias: parametros.get('minimoDeDivergencias') || '',
    maximoDeDivergencias: parametros.get('maximoDeDivergencias') || '',
    executor: parametros.get('executor') || '',
    pagina: Number(parametros.get('pagina') || 0),
  };

  const resultado = el('div', { 'aria-live': 'polite' });
  trocar(raiz, [
    el('div', { classe: 'tela-historico' }, [
      el('header', { classe: 'hist-cabecalho' }, [
        el('h1', { texto: 'Analises anteriores' }),
        el('p', { classe: 'hist-sub', texto: 'Da mais recente para a mais antiga. Clique numa linha para reabrir o resultado.' }),
      ]),
      resultado,
    ]),
  ]);
  await carregar(resultado, filtros);
}

async function carregar(resultado, filtros) {
  trocar(resultado, [el('p', { classe: 'carregando', role: 'status', texto: 'Buscando no historico...' })]);
  let resposta;
  try {
    resposta = await api.historico(consultaDe(filtros));
  } catch (erro) {
    trocar(resultado, [falha(erro)]);
    return;
  }
  trocar(resultado, [
    formularioDeFiltros(resposta, filtros, (novos) => carregar(resultado, { ...novos, pagina: 0 })),
    corpo(resposta, filtros, (pagina) => carregar(resultado, { ...filtros, pagina })),
  ]);
}

function consultaDe(filtros) {
  const consulta = { pagina: filtros.pagina, tamanho: TAMANHO };
  for (const campo of ['de', 'ate', 'situacaoMaisGrave', 'minimoDeDivergencias', 'maximoDeDivergencias']) {
    if (filtros[campo] !== '') consulta[campo] = filtros[campo];
  }
  if (filtros.executor === '(nao-registrado)') {
    consulta.semExecutorRegistrado = 'true';
  } else if (filtros.executor !== '') {
    consulta.executor = filtros.executor;
  }
  return consulta;
}

function campo(id, rotulo, controle, ajuda) {
  const info = ajuda ? botaoDeInformacao('Como funciona: ' + rotulo, el('p', { texto: ajuda })) : null;
  if (info) {
    controle.setAttribute('aria-describedby', info.balao.getAttribute('id'));
  }
  return el('div', { classe: 'hist-campo' }, [
    el('div', { classe: 'hist-rotulo' }, [
      el('label', { for: id, texto: rotulo }),
      info ? info.botao : null,
    ]),
    controle,
    info ? info.balao : null,
  ]);
}

function formularioDeFiltros(resposta, filtros, aoFiltrar) {
  const de = el('input', { type: 'date', id: 'filtro-de', value: filtros.de });
  const ate = el('input', { type: 'date', id: 'filtro-ate', value: filtros.ate });
  const situacoes = [
    ['', 'Sem Filtro'],
    ['POSSIVEL_DIVERGENCIA', 'Possivel divergencia'],
    ['REQUER_CONFERENCIA', 'Requer conferencia'],
    ['NAO_FOI_POSSIVEL_CONCLUIR', 'Nao foi possivel concluir'],
    ['SEM_DIVERGENCIA_IDENTIFICADA', 'Sem divergencia identificada'],
  ];
  const situacao = el('select', { id: 'filtro-situacao' },
    situacoes.map(([valor, rotulo]) => el('option', {
      value: valor, texto: rotulo, selected: valor === filtros.situacaoMaisGrave ? 'selected' : null,
    })));
  const minimo = el('input', { type: 'number', min: '0', id: 'filtro-minimo', value: filtros.minimoDeDivergencias });
  const maximo = el('input', { type: 'number', min: '0', id: 'filtro-maximo', value: filtros.maximoDeDivergencias });
  const executor = el('select', { id: 'filtro-executor' }, [
    el('option', { value: '', texto: 'Sem Filtro' }),
    el('option', {
      value: '(nao-registrado)', texto: 'Usuario nao registrado',
      selected: filtros.executor === '(nao-registrado)' ? 'selected' : null,
    }),
    ...resposta.executoresConhecidos.map((conhecido) => el('option', {
      value: conhecido.login,
      texto: conhecido.nome + ' (' + conhecido.login + ')' + (conhecido.ativo ? '' : ', desativado'),
      selected: conhecido.login === filtros.executor ? 'selected' : null,
    })),
  ]);

  const formulario = el('form', { classe: 'hist-filtros', 'aria-label': 'Filtros do historico' }, [
    campo('filtro-de', 'De', de),
    campo('filtro-ate', 'Ate', ate),
    campo('filtro-situacao', resposta.rotuloDoFiltroDeSituacao, situacao, resposta.explicacaoDoFiltroDeSituacao),
    campo('filtro-executor', 'Quem executou', executor),
    campo('filtro-minimo', resposta.rotuloDoFiltroDeDivergencias + ': no minimo', minimo),
    campo('filtro-maximo', resposta.rotuloDoFiltroDeDivergencias + ': no maximo', maximo),
    el('div', { classe: 'hist-acoes' }, [
      el('button', { type: 'submit', classe: 'hist-botao', texto: 'Filtrar' }),
      el('button', {
        type: 'button', classe: 'hist-botao', texto: 'Limpar filtros',
        aoClicar: () => aoFiltrar({ de: '', ate: '', situacaoMaisGrave: '', minimoDeDivergencias: '', maximoDeDivergencias: '', executor: '' }),
      }),
    ]),
  ]);
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    aoFiltrar({
      de: de.value, ate: ate.value, situacaoMaisGrave: situacao.value,
      minimoDeDivergencias: minimo.value, maximoDeDivergencias: maximo.value, executor: executor.value,
    });
  });
  return formulario;
}

function algumFiltro(filtros) {
  return ['de', 'ate', 'situacaoMaisGrave', 'minimoDeDivergencias', 'maximoDeDivergencias', 'executor']
    .some((nome) => filtros[nome] !== '');
}

function contagensEmLinha(contagens) {
  return el('span', { classe: 'hist-contagens' }, contagens.map((contagem) => {
    const s = selo(contagem.estado, contagem.rotulo, contagem.rotulo + ': ' + inteiro(contagem.quantidade));
    s.className += ' hist-contagem' + (contagem.quantidade === 0 ? ' zero' : '');
    s.appendChild(el('strong', { texto: inteiro(contagem.quantidade) }));
    return s;
  }));
}

function legendaDosEstados(contagens) {
  return el('div', { classe: 'hist-legenda', 'aria-hidden': 'true' }, [
    el('span', { classe: 'hist-legenda-titulo', texto: 'Legenda' }),
    ...contagens.map((contagem) => {
      const s = selo(contagem.estado, contagem.rotulo, contagem.explicacao);
      s.className += ' hist-legenda-item';
      return s;
    }),
  ]);
}

function naoRegistrado(motivo) {
  return el('span', { classe: 'ausente', title: motivo }, [
    'nao registrado',
    el('span', { classe: 'so-leitor-de-tela', texto: ' — ' + motivo }),
  ]);
}

function avisoDasNaoMedidas(resposta, filtros) {
  const filtroQueNaoClassifica = filtros.situacaoMaisGrave || filtros.minimoDeDivergencias
    || filtros.maximoDeDivergencias;
  if (!filtroQueNaoClassifica || !resposta.execucoesNaoMedidasNoResultado) {
    return null;
  }
  return el('p', { classe: 'hist-aviso', role: 'note' }, [
    el('strong', { texto: inteiro(resposta.execucoesNaoMedidasNoResultado)
      + ' execucao(oes) sem contagem medida estao incluidas neste resultado. ' }),
    resposta.explicacaoDasNaoMedidas,
  ]);
}

function executorDa(linha) {
  if (linha.executor) {
    const nome = linha.executor.nome + ' (' + linha.executor.login + ')';
    return el('span', { classe: 'hist-trunca', title: nome, texto: nome });
  }
  return el('span', { classe: 'ausente', title: linha.motivoDoExecutorAusente }, [
    'nao registrado',
    el('span', { classe: 'so-leitor-de-tela', texto: ' — ' + linha.motivoDoExecutorAusente }),
  ]);
}

function corpo(resposta, filtros, aoPaginar) {
  if (resposta.linhas.length === 0) {
    return algumFiltro(filtros)
      ? el('p', { classe: 'estado-vazio', texto: 'Nenhuma analise atende aos filtros. Limpe algum filtro para ver mais.' })
      : el('div', { classe: 'estado-vazio' }, [
        el('p', { texto: 'Nenhuma analise ainda.' }),
        el('a', { classe: 'hist-botao', href: enderecoDeEnvio(), texto: 'Analisar a primeira nota' }),
      ]);
  }

  const linhas = resposta.linhas.map((linha) => {
    const destino = enderecoDoResultado(linha.id);
    const tr = el('tr', { classe: 'hist-linha' }, [
      el('td', {}, [
        linha.situacaoMaisGrave
          ? selo(linha.situacaoMaisGrave, linha.rotuloDaSituacaoMaisGrave, null)
          : (linha.produtosPorSituacao
            ? valorOuMotivo(null, linha.motivoDaSituacaoAusente)
            : naoRegistrado(linha.motivoDaSituacaoAusente)),
      ]),
      el('td', { classe: 'hist-quando' }, [el('a', { href: destino, texto: dataHora(linha.dataHora) })]),
      el('td', {}, [linha.produtosPorSituacao
        ? contagensEmLinha(linha.produtosPorSituacao)
        : naoRegistrado(linha.motivoDaContagemAusente)]),
      linha.produtosPorSituacao
        ? el('td', {
          classe: 'numero' + (linha.produtosComAlgumaVerificacaoNaoConcluida === 0 ? ' zero' : ''),
          texto: inteiro(linha.produtosComAlgumaVerificacaoNaoConcluida),
        })
        : el('td', { classe: 'numero' }, [naoRegistrado(linha.motivoDaContagemAusente)]),
      el('td', {}, [
        el('span', { classe: 'mono hist-trunca', title: linha.versaoDoCatalogo, texto: linha.versaoDoCatalogo }),
      ]),
      el('td', {}, [
        el('span', { classe: 'mono hist-trunca', title: linha.versaoDasRegras, texto: linha.versaoDasRegras }),
      ]),
      el('td', { classe: 'hist-executor' }, [executorDa(linha)]),
      el('td', { classe: 'numero', texto: inteiro(linha.documentos) + ' / ' + inteiro(linha.itens) }),
    ]);
    tr.addEventListener('click', (evento) => {
      if (!evento.target.closest('a, button')) {
        irPara(destino);
      }
    });
    return tr;
  });

  const pagina = resposta.pagina;
  const comContagem = resposta.linhas.find((linha) => linha.produtosPorSituacao);
  const cartao = el('div', { classe: 'hist-cartao' }, [
    comContagem ? legendaDosEstados(comContagem.produtosPorSituacao) : null,
    el('div', { classe: 'hist-rolagem' }, [
      el('table', { classe: 'data-table' }, [
        el('caption', {
          texto: inteiro(pagina.totalDeLinhas) + ' analise(s); ' + resposta.ordem + '.',
        }),
        el('thead', {}, [el('tr', {}, [
          el('th', { scope: 'col', texto: resposta.rotuloDoFiltroDeSituacao }),
          el('th', { scope: 'col', texto: 'Data' }),
          el('th', { scope: 'col', texto: 'Produtos por situacao' }),
          el('th', { scope: 'col', classe: 'numero', title: 'produtos com alguma verificacao sem conclusao',
            texto: 'Sem conclusao' }),
          el('th', { scope: 'col', texto: 'Catalogo' }),
          el('th', { scope: 'col', texto: 'Regras' }),
          el('th', { scope: 'col', texto: 'Executada por' }),
          el('th', { scope: 'col', classe: 'numero', texto: 'Docs / itens' }),
        ])]),
        el('tbody', {}, linhas),
      ]),
    ]),
    pagina.totalDePaginas > 1 ? el('div', { classe: 'hist-paginacao' }, [
      el('button', {
        type: 'button', classe: 'hist-botao', disabled: pagina.numero === 0 ? 'disabled' : null,
        texto: 'anterior', aoClicar: () => aoPaginar(pagina.numero - 1),
      }),
      el('span', { texto: 'pagina ' + inteiro(pagina.numero + 1) + ' de ' + inteiro(pagina.totalDePaginas) }),
      el('button', {
        type: 'button', classe: 'hist-botao', disabled: pagina.numero + 1 >= pagina.totalDePaginas ? 'disabled' : null,
        texto: 'proxima', aoClicar: () => aoPaginar(pagina.numero + 1),
      }),
    ]) : null,
  ]);
  return el('div', {}, [
    avisoDasNaoMedidas(resposta, filtros),
    cartao,
    el('p', { classe: 'hist-atalho' }, [
      el('a', { href: 'tecnica.html#/', texto: 'Ver as execucoes na visao tecnica' }),
    ]),
  ]);
}
