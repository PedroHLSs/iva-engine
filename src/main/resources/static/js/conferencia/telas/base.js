import { el, trocar } from '../../dom.js';
import { data as formatarData } from '../../formato.js';
import { botaoDeInformacao } from '../../painel.js';
import * as api from '../api.js';
import { avisoDeUso, leituraDoCatalogo, procedenciaCompacta, valorOuMotivo } from '../pecas.js';
import { enderecoDaBase, irPara } from '../roteador.js';

export async function desenhar(raiz, parametros, rota) {
  const dataPedida = rota.parametros.get('data') || '';
  const ncmPedido = rota.parametros.get('ncm') || '';
  const classTribPedido = rota.parametros.get('cClassTrib') || '';

  const corpo = el('div', {});
  const info = botaoDeInformacao('Sobre esta consulta', [
    el('p', {
      texto: 'Consulta somente leitura do catalogo importado. Toda resposta e datada, porque o '
        + 'mesmo catalogo responde coisas diferentes em datas diferentes — e por isso a data e '
        + 'obrigatoria.',
    }),
  ]);

  trocar(raiz, [
    el('div', { classe: 'tela-base' }, [
      el('header', { classe: 'base-cabecalho' }, [
        el('div', { classe: 'base-titulo' }, [
          el('h1', { texto: 'Base tributaria carregada' }),
          info.botao,
        ]),
        el('p', { classe: 'base-sub', texto: 'Consulta somente leitura, sempre numa data escolhida.' }),
        info.balao,
      ]),
      formulario(dataPedida, ncmPedido, classTribPedido),
      corpo,
    ]),
  ]);

  if (!dataPedida) {
    trocar(corpo, [
      el('p', {
        classe: 'base-vazio',
        texto: 'Escolha uma data para consultar. Sem data a resposta nao diz a que dia se refere.',
      }),
    ]);
    return;
  }

  trocar(corpo, [el('p', { classe: 'carregando', texto: 'Consultando...' })]);
  const resposta = await api.baseTributaria(dataPedida, ncmPedido, classTribPedido);
  trocar(corpo, [conteudo(resposta)]);
}

function formulario(dataPedida, ncmPedido, classTribPedido) {
  const campoData = el('input', { type: 'date', id: 'data', value: dataPedida || null });
  const campoNcm = el('input', {
    type: 'text', id: 'ncm', value: ncmPedido || null, inputmode: 'numeric', maxlength: '8',
    placeholder: '8 digitos',
  });
  const campoClassTrib = el('input', {
    type: 'text', id: 'cClassTrib', value: classTribPedido || null,
  });

  const consultar = () => irPara(enderecoDaBase({
    data: campoData.value,
    ncm: campoNcm.value,
    cClassTrib: campoClassTrib.value,
  }));

  for (const campo of [campoData, campoNcm, campoClassTrib]) {
    campo.addEventListener('keydown', (evento) => {
      if (evento.key === 'Enter') {
        consultar();
      }
    });
  }

  return el('div', { classe: 'base-filtros', role: 'search' }, [
    el('div', { classe: 'base-campo' }, [
      el('label', { for: 'data', texto: 'Data (obrigatoria)' }),
      el('div', { classe: 'base-campo-data' }, [
        campoData,
        el('button', {
          type: 'button',
          classe: 'base-botao-secundario',
          texto: 'hoje',
          title: 'preenche a data com a de hoje — a escolha continua sendo sua, e fica escrita',
          aoClicar: () => {
            const agora = new Date();
            const mes = String(agora.getMonth() + 1).padStart(2, '0');
            const dia = String(agora.getDate()).padStart(2, '0');
            campoData.value = `${agora.getFullYear()}-${mes}-${dia}`;
          },
        }),
      ]),
    ]),
    el('div', { classe: 'base-campo' }, [
      el('label', { for: 'ncm', texto: 'NCM (opcional)' }),
      campoNcm,
    ]),
    el('div', { classe: 'base-campo' }, [
      el('label', { for: 'cClassTrib', texto: 'cClassTrib (opcional)' }),
      campoClassTrib,
    ]),
    el('button', { type: 'button', classe: 'base-botao-principal', texto: 'Consultar',
      aoClicar: consultar }),
  ]);
}

function conteudo(resposta) {
  return el('div', {}, [
    procedenciaCompacta(resposta.natureza),

    el('section', { classe: 'base-cartao' }, [
      el('h2', { texto: 'Carga consultada' }),
      el('dl', { classe: 'base-grade' }, [
        par('Data da consulta', formatarData(resposta.data)),
        par('Versao do catalogo', resposta.versaoDoCatalogo),
        par('Carga gravada', resposta.cargaDisponivel ? 'sim' : 'nao'),
      ]),
    ]),

    cartao('Cobertura declarada da carga', leituraDoCatalogo(resposta.cobertura, (linhas) =>
      tabela(['Tabela', 'Vigencia', 'Fonte'], linhas.map((linha) => [
        el('td', { classe: 'forte', texto: linha.tabela }),
        ...celulasDaReferencia(linha.referencia),
      ])))),

    cartao('Aliquotas vigentes na data', tabelaDeAliquotas(resposta.aliquotas)),

    blocoConsultado('NCM consultado', resposta.ncmPerguntado, resposta.ncmConsultado,
      (registros) => tabela(['NCM', 'Descricao', 'Vigencia', 'Fonte'], registros.map((registro) => [
        el('td', { classe: 'mono forte', texto: registro.ncm }),
        el('td', { texto: registro.descricao }),
        ...celulasDaReferencia(registro.referencia),
      ]))),

    blocoConsultado('Anexos do NCM consultado', resposta.ncmPerguntado, resposta.anexosDoNcm,
      (registros) => tabela(['Anexo', 'Tratamento', 'Vigencia', 'Fonte'], registros.map((registro) => [
        el('td', { classe: 'forte', texto: registro.anexo }),
        el('td', { texto: registro.tipoDeTratamento }),
        ...celulasDaReferencia(registro.referencia),
      ]))),

    blocoConsultado('cClassTrib consultado', resposta.classTribPerguntado,
      resposta.classificacaoConsultada,
      (registros) => tabela(
        ['Codigo', 'Dispositivo legal', 'CST admitidos', 'Reducao', 'Vigencia', 'Fonte'],
        registros.map((registro) => [
          el('td', { classe: 'mono forte', texto: registro.codigo }),
          el('td', { texto: registro.dispositivoLegal }),
          el('td', {}, [registro.cstsAdmitidos.length
            ? el('span', { classe: 'mono', texto: registro.cstsAdmitidos.join(', ') })
            : valorOuMotivo(null, registro.motivoSemCstAdmitido)]),
          el('td', { classe: 'mono' }, [valorOuMotivo(registro.percentualReducao, registro.motivoSemReducao)]),
          ...celulasDaReferencia(registro.referencia),
        ]))),

    el('footer', { classe: 'base-rodape' }, [
      el('p', { texto: resposta.comoConsultar }),
      avisoDeUso(resposta.aviso),
    ]),
  ]);
}

function tabelaDeAliquotas(tributos) {
  const colunas = ['Imposto / parcela', 'Aliquota', 'Abrangencia', 'Vigencia', 'Fonte'];
  const linhas = (tributos || []).flatMap((doTributo) => {
    const leitura = doTributo.aliquotas;
    const encontrado = (leitura && leitura.encontrado) || [];
    if (encontrado.length === 0) {
      return [[
        el('td', { classe: 'forte', texto: doTributo.rotulo }),
        el('td', { colspan: String(colunas.length - 1), classe: 'ausente',
          texto: leitura ? leitura.motivoDaAusencia : 'a resposta nao trouxe este bloco' }),
      ]];
    }
    return encontrado.map((aliquota) => [
      el('td', { classe: 'forte', texto: doTributo.rotulo }),
      el('td', { classe: 'mono base-percentual', texto: aliquota.percentual }),
      el('td', { texto: aliquota.abrangencia }),
      ...celulasDaReferencia(aliquota.referencia),
    ]);
  });
  if (linhas.length === 0) {
    return el('p', { classe: 'ausente', texto: 'a resposta nao trouxe nenhum tributo' });
  }
  return tabela(colunas, linhas);
}

function blocoConsultado(titulo, perguntado, leitura, comoDesenhar) {
  if (perguntado === null || perguntado === undefined) {
    return null;
  }
  return el('section', { classe: 'base-cartao' }, [
    el('div', { classe: 'base-cartao-titulo' }, [
      el('h2', { texto: titulo }),
      el('span', { classe: 'base-perguntado' }, ['perguntado: ', el('span', { classe: 'mono', texto: perguntado })]),
    ]),
    leituraDoCatalogo(leitura, comoDesenhar),
  ]);
}

function cartao(titulo, conteudoDoCartao) {
  return el('section', { classe: 'base-cartao' }, [el('h2', { texto: titulo }), conteudoDoCartao]);
}

function par(chave, valor) {
  return el('div', { classe: 'base-par' }, [
    el('dt', { texto: chave }),
    el('dd', { texto: valor }),
  ]);
}

function tabela(colunas, linhas) {
  return el('div', { classe: 'base-rolagem' }, [
    el('table', { classe: 'data-table' }, [
      el('thead', {}, [el('tr', {}, colunas.map((coluna) => el('th', { scope: 'col', texto: coluna })))]),
      el('tbody', {}, linhas.map((celulas) => el('tr', {}, celulas))),
    ]),
  ]);
}

function celulasDaReferencia(ref) {
  if (!ref) {
    return [el('td', { colspan: '2', classe: 'ausente', texto: 'sem referencia na resposta' })];
  }
  const fim = ref.vigenciaFim
    ? el('span', { texto: formatarData(ref.vigenciaFim) })
    : el('span', { classe: 'ausente', title: ref.motivoDaVigenciaSemFim }, [
      '(nao declarado)',
      el('span', { classe: 'so-leitor-de-tela', texto: ' — ' + ref.motivoDaVigenciaSemFim }),
    ]);
  return [
    el('td', { classe: 'base-vigencia' }, [
      el('span', { classe: 'mono', texto: formatarData(ref.vigenciaInicio) }),
      ' ate ',
      fim,
    ]),
    el('td', { classe: 'base-fonte', texto: ref.fonteNormativa }),
  ];
}
