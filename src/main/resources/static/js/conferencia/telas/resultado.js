import { el, trocar } from '../../dom.js';
import { dataHora, documentoCurto, inteiro, rotuloDaRegra } from '../../formato.js';
import * as api from '../api.js';
import {
  avisoDeUso, blocoDaLeitura, cabecalhoDoResultado, falha, leituraRegistrada, procedenciaCompacta,
  produtosComPendencia, quadroDeEstados, valorOuMotivo,
} from '../pecas.js';
import { enderecoDeEnvio, enderecoDoResultado, irPara } from '../roteador.js';
import { quemEsta } from '../../sessao.js';
import { abas } from '../abas.js';

import { identificacaoDoDocumento, painelDeProdutos } from './nota.js';
import { painelDeAgrupamentos } from './lote.js';

const TAMANHO_DOS_NAO_CONCLUIDOS = 50;

export async function desenhar(raiz, parametros, rota) {
  const resposta = await api.analise(rota.analiseId);
  const conferencia = resposta.conferencia;
  const lote = conferencia.quantidadeDeNotas > 1;

  if (conferencia.quantidadeDeProdutos === 0 && resposta.leitura.itensLidos > 0) {
    trocar(raiz, [
      cabecalhoDoResultado(
        'Analise sem acervo de produtos',
        'Esta execucao leu itens, mas nao registrou quais. Nao ha como listar os produtos dela.',
        resposta.natureza,
      ),
      el('p', {
        texto: 'Execucoes feitas pelo comando "auditar", na linha de comando, gravam apontamentos '
          + 'mas nao gravam o acervo do que leram — ele so passou a existir com a analise por '
          + 'esta tela. Os apontamentos continuam disponiveis na visao tecnica.',
      }),
      blocoDaLeitura(resposta.leitura),
      el('div', { classe: 'acoes' }, [
        el('a', { classe: 'botao', href: 'tecnica.html#/execucao/' + encodeURIComponent(rota.analiseId),
          texto: 'Abrir na visao tecnica' }),
        el('a', { classe: 'botao', href: enderecoDeEnvio(), texto: 'Enviar outra nota' }),
      ]),
      avisoDeUso(resposta.aviso),
    ]);
    return;
  }

  const primeiraDosNaoConcluidos = await api.naoConcluidas(rota.analiseId, 0, 1);
  const quantosNaoConcluidos = primeiraDosNaoConcluidos.pagina.totalDeElementos;

  const definicoes = [
    { chave: 'resumo', rotulo: 'Resumo', desenhar: (painel) => painelDoResumo(painel, resposta, rota.analiseId) },
    { chave: 'produtos', rotulo: 'Produtos', desenhar: (painel) => painelDeProdutos(painel, rota.analiseId) },
    {
      chave: 'nao-concluidos',
      rotulo: 'Nao concluidos (' + inteiro(quantosNaoConcluidos) + ')',
      desenhar: (painel) => painelDosNaoConcluidos(painel, rota.analiseId, 0),
    },
    lote ? {
      chave: 'agrupamentos',
      rotulo: 'Agrupamentos',
      desenhar: (painel) => painelDeAgrupamentos(painel, rota.analiseId, rota.parametros.get('ordem') || null),
    } : null,
    { chave: 'detalhes', rotulo: 'Detalhes tecnicos', desenhar: (painel) => painelDeDetalhes(painel, resposta, rota.analiseId) },
  ].filter(Boolean);

  const blocoDeAbas = abas(definicoes, parametros.get('aba'), (escolhida) => {
    if (window.history && window.history.replaceState) {
      window.history.replaceState(null, '', enderecoDoResultado(rota.analiseId, { aba: escolhida }));
    }
  });

  trocar(raiz, [
    el('header', { classe: 'cabecalho-resultado' }, [
      el('h1', { texto: lote ? 'Resultado do lote' : 'Resultado da nota' }),
      el('p', {
        classe: 'sub',
        texto: (lote ? inteiro(conferencia.quantidadeDeNotas) + ' nota(s), ' : '')
          + inteiro(conferencia.quantidadeDeProdutos) + ' produto(s), analisado(s) em '
          + dataHora(resposta.leitura.dataHora) + '.',
      }),
      procedenciaCompacta(resposta.natureza),
    ]),
    resumoFixo(resposta),
    blocoDeAbas,
    avisoDeUso(resposta.aviso),
  ]);
  await blocoDeAbas.pronto;
}

function resumoFixo(resposta) {
  const conferencia = resposta.conferencia;
  const leitura = resposta.leitura;
  return el('section', { classe: 'resumo-fixo', 'aria-label': 'Resumo da analise' }, [
    quadroDeEstados(conferencia.produtosPorSituacao, 'Situacao dos produtos', { compacto: true }),
    produtosComPendencia(
      conferencia.produtosComAlgumaVerificacaoNaoConcluida,
      conferencia.quantidadeDeProdutos,
    ),
    ilegiveisNoResumo(leitura),
    el('p', { classe: 'nota versoes-do-resumo' }, [
      'Catalogo ', el('strong', { classe: 'mono', texto: leitura.versaoCatalogo }),
      ' · regras ', el('strong', { classe: 'mono', texto: leitura.versaoConjuntoRegras }),
      ' · tolerancia R05 ', el('strong', { classe: 'mono', texto: textoDaTolerancia(leitura.toleranciaDeValor) }),
    ]),
  ]);
}

function ilegiveisNoResumo(leitura) {
  if (!leituraRegistrada(leitura)) {
    return el('p', { classe: 'destaque-ausencia' }, [
      'Arquivos que nao puderam ser lidos: nao registrado. ',
      valorOuMotivo(null, leitura.motivoDosArquivosIlegiveisAusentes),
    ]);
  }
  return el('p', { classe: leitura.arquivosIlegiveis > 0 ? 'destaque-ausencia' : 'nota' }, [
    inteiro(leitura.arquivosIlegiveis) + ' arquivo(s) nao puderam ser lidos',
    leitura.arquivosIlegiveis > 0 ? ' — contados a parte, e nunca como nota sem divergencia. '
      + 'A lista esta em "Detalhes tecnicos".' : '.',
  ]);
}

const textoDaTolerancia = (tolerancia) => (tolerancia && tolerancia.texto) || (tolerancia && tolerancia.motivoDaAusencia)
  || 'a resposta nao trouxe a tolerancia';

async function painelDoResumo(painel, resposta, analiseId) {
  const conferencia = resposta.conferencia;
  const partes = [];
  if (conferencia.quantidadeDeNotas <= 1) {
    const primeiro = await api.produtos(analiseId, 0, 1);
    if (primeiro.produtos[0]) {
      partes.push(identificacaoDoDocumento(primeiro.produtos[0].documento));
    }
  }
  trocar(painel, [
    ...partes,
    quadroDeEstados(conferencia.verificacoesPorEstado, 'Verificacoes, regra a regra', {
      compacto: true, nota: conferencia.comoFoiObtido,
    }),
  ]);
}

async function painelDosNaoConcluidos(painel, analiseId, pagina) {
  trocar(painel, [el('p', { classe: 'carregando', role: 'status', texto: 'Carregando as verificacoes sem conclusao...' })]);
  const resposta = await api.naoConcluidas(analiseId, pagina, TAMANHO_DOS_NAO_CONCLUIDOS);
  if (resposta.pagina.totalDeElementos === 0) {
    trocar(painel, [
      el('h2', { texto: 'Nao concluidos' }),
      el('p', {
        classe: 'estado-vazio',
        texto: 'Nenhuma verificacao ficou sem conclusao nesta analise: todas as regras chegaram a um '
          + 'resultado sobre os campos que alcancam.',
      }),
    ]);
    return;
  }
  const linhas = resposta.naoAvaliados.map((linha) => el('tr', {}, [
    el('td', { texto: documentoCurto(linha.documento) }),
    el('td', { texto: String(linha.numeroItem) }),
    el('td', {}, [rotuloDaRegra(linha)]),
    el('td', { texto: linha.motivo }),
  ]));
  trocar(painel, [
    el('h2', { texto: 'Nao concluidos' }),
    el('p', {
      classe: 'nota',
      texto: 'Verificacao sem conclusao nao e conforme: faltou dado no documento ou na base normativa '
        + 'carregada. O motivo e o que a propria regra escreveu.',
    }),
    el('div', { classe: 'rolagem' }, [
      el('table', { classe: 'tabela' }, [
        el('thead', {}, [el('tr', {}, [
          el('th', { scope: 'col', texto: 'nota' }),
          el('th', { scope: 'col', texto: 'item' }),
          el('th', { scope: 'col', texto: 'regra' }),
          el('th', { scope: 'col', texto: 'motivo' }),
        ])]),
        el('tbody', {}, linhas),
      ]),
    ]),
    paginacao(resposta.pagina, (destino) => painelDosNaoConcluidos(painel, analiseId, destino)),
  ]);
}

function paginacao(pagina, aoIr) {
  if (pagina.totalDePaginas <= 1) {
    return null;
  }
  return el('div', { classe: 'acoes paginacao' }, [
    el('button', {
      type: 'button', classe: 'botao', disabled: pagina.numero === 0 ? 'disabled' : null,
      texto: 'anterior', aoClicar: () => aoIr(pagina.numero - 1),
    }),
    el('span', {
      classe: 'nota',
      texto: 'pagina ' + inteiro(pagina.numero + 1) + ' de ' + inteiro(pagina.totalDePaginas),
    }),
    el('button', {
      type: 'button', classe: 'botao', disabled: pagina.numero + 1 >= pagina.totalDePaginas ? 'disabled' : null,
      texto: 'proxima', aoClicar: () => aoIr(pagina.numero + 1),
    }),
  ]);
}

async function painelDeDetalhes(painel, resposta, analiseId) {
  const [autoria, correcao] = await Promise.all([api.autoria(analiseId), blocoDeCorrecao(analiseId)]);
  const leitura = resposta.leitura;
  trocar(painel, [
    el('section', { classe: 'bloco' }, [
      el('h2', { texto: 'Identificacao da execucao' }),
      el('dl', { classe: 'identificacao' }, [
        el('dt', { texto: 'identificador' }), el('dd', { classe: 'mono', texto: leitura.id }),
        el('dt', { texto: 'data e hora' }), el('dd', { texto: dataHora(leitura.dataHora) }),
        el('dt', { texto: 'versao do catalogo' }), el('dd', { classe: 'mono', texto: leitura.versaoCatalogo }),
        el('dt', { texto: 'versao das regras' }), el('dd', { classe: 'mono', texto: leitura.versaoConjuntoRegras }),
        el('dt', { texto: 'executada por' }),
        el('dd', {}, [autoria.executor
          ? document.createTextNode(autoria.executor.nome + ' (' + autoria.executor.login + ')'
            + (autoria.executor.ativo ? '' : ', desativado'))
          : valorOuMotivo(null, autoria.motivoDoExecutorAusente)]),
      ]),
    ]),
    blocoDaLeitura(leitura),
    correcao,
    el('div', { classe: 'acoes' }, [
      el('a', { classe: 'botao', href: 'tecnica.html#/execucao/' + encodeURIComponent(analiseId),
        texto: 'Abrir na visao tecnica' }),
      el('a', { classe: 'botao', href: enderecoDeEnvio(), texto: 'Enviar outra nota' }),
    ]),
  ]);
}

async function blocoDeCorrecao(analiseId) {
  const [vinculos, usuario] = await Promise.all([api.vinculos(analiseId), quemEsta()]);
  const resultado = el('div', { role: 'status' });
  const campo = el('input', { type: 'file', id: 'arquivo-corrigido', accept: '.xml,.zip' });
  campo.addEventListener('change', async () => {
    const arquivo = campo.files && campo.files[0];
    if (!arquivo) {
      return;
    }
    trocar(resultado, [el('p', { classe: 'estado-do-envio', texto: 'Analisando a correcao...' })]);
    try {
      const nova = await api.corrigir(analiseId, arquivo);
      irPara(enderecoDoResultado(nova.leitura.id));
    } catch (recusa) {
      trocar(resultado, [falha(recusa)]);
    }
  });

  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Correcao' }),
    vinculos.corrige
      ? el('p', {}, ['Esta analise corrige ', el('a', { href: enderecoDoResultado(vinculos.corrige), texto: 'a anterior' }), '.'])
      : el('p', { classe: 'nota', texto: vinculos.motivoSemCorrecao }),
    vinculos.corrigidaPor.length
      ? el('p', {}, ['Foi corrigida depois por ',
        ...vinculos.corrigidaPor.map((id, posicao) => el('a', {
          href: enderecoDoResultado(id), texto: (posicao ? ', ' : '') + 'outra analise',
        })), '.'])
      : null,
    usuario && usuario.permissoes.enviarNota
      ? el('div', {}, [
        el('label', {
          for: 'arquivo-corrigido',
          texto: 'Enviar o arquivo corrigido. Sai uma analise nova, ligada a esta; esta nao muda.',
        }),
        campo,
        resultado,
      ])
      : null,
  ]);
}
