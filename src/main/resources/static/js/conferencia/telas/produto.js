import { el, trocar } from '../../dom.js';
import { recolhivel } from '../../colapso.js';
import { data, documentoCurto, rotuloDaRegra } from '../../formato.js';
import { botaoDeInformacao } from '../../painel.js';
import * as api from '../api.js';
import {
  avisoDeUso, leituraDoCatalogo, procedenciaCompacta, selo, valorOuMotivo,
} from '../pecas.js';
import { enderecoDoResultado } from '../roteador.js';
import { chaveDeAcesso } from './nota.js';

export async function desenhar(raiz, parametros, rota) {
  const resposta = await api.produto(rota.analiseId, rota.endereco);
  const produto = resposta.produto;

  trocar(raiz, [
    el('a', { classe: 'voltar', href: enderecoDoResultado(rota.analiseId), texto: 'Voltar ao resultado' }),
    cabecalho(produto, resposta),
    blocoDosCampos(resposta.declarado, resposta.comparacao),
    blocoDasDescricoes(resposta.descricoes),
    blocoDoTratamento(resposta.tratamento),
    blocoDosPassos(resposta.passos),
    avisoDeUso(resposta.aviso),
  ]);
}

function cabecalho(produto, resposta) {
  const documento = resposta.documento;
  const sobreASituacao = botaoDeInformacao('Como a situacao foi obtida', [
    el('p', { texto: produto.explicacaoDaSituacao }),
    el('p', { texto: produto.comoASituacaoFoiObtida }),
  ]);
  const par = (chave, conteudo, classe) => el('div', { classe: 'meta-par' }, [
    el('dt', { texto: chave }),
    el('dd', { classe: classe || null }, [].concat(conteudo)),
  ]);

  return el('header', { classe: 'cabecalho-resultado cabecalho-do-produto' }, [
    el('div', { classe: 'titulo-de-secao-linha' }, [
      el('h1', { texto: 'Produto ' + produto.numeroItem + ' da nota ' + documentoCurto(documento) }),
      selo(produto.situacao, produto.rotuloDaSituacao, produto.explicacaoDaSituacao),
      sobreASituacao.botao,
    ]),
    sobreASituacao.balao,
    procedenciaCompacta(resposta.natureza),
    produto.reprocessadoDepoisDestaAnalise
      ? el('div', { classe: 'aviso', texto: produto.avisoDeReprocessamento })
      : null,
    el('dl', { classe: 'metadados' }, [
      par('nota', documentoCurto(documento)),
      par('emissao', data(documento.dataEmissao)),
      par('UF do emitente', valorOuMotivo(documento.ufEmitente, null)),
      par('chave de acesso', chaveDeAcesso(documento), documento.chaveAcesso ? 'mono quebra-tudo' : null),
      par('tolerancia R05', (resposta.toleranciaDeValor && resposta.toleranciaDeValor.texto)
        || (resposta.toleranciaDeValor && resposta.toleranciaDeValor.motivoDaAusencia)
        || 'a resposta nao trouxe a tolerancia', 'mono'),
    ]),
  ]);
}

function blocoDosCampos(declarado, comparacao) {
  const porCampo = new Map(comparacao.linhas.map((linha) => [linha.campo, linha]));
  const usadas = new Set();

  const indicado = (linha) => leituraDoCatalogo(linha.indicado, (valores) =>
    el('span', { classe: 'mono', texto: valores.join(' | ') }));

  const linhas = declarado.campos.map((campo) => {
    const comparavel = porCampo.get(campo.campo);
    if (comparavel) {
      usadas.add(campo.campo);
    }
    return el('tr', {}, [
      el('th', { scope: 'row', texto: campo.campo }),
      el('td', {}, [valorOuMotivo(campo.valor, campo.motivoDaAusencia)]),
      el('td', {}, [comparavel
        ? indicado(comparavel)
        : el('span', { classe: 'sem-indicacao', texto: 'sem indicacao na carga' })]),
    ]);
  });
  for (const linha of comparacao.linhas) {
    if (!usadas.has(linha.campo)) {
      linhas.push(el('tr', {}, [
        el('th', { scope: 'row', texto: linha.campo }),
        el('td', {}, [valorOuMotivo(linha.declarado, linha.motivoDoNaoDeclarado)]),
        el('td', {}, [indicado(linha)]),
      ]));
    }
  }

  const quemJulga = botaoDeInformacao('Quem julga', el('p', { texto: comparacao.quemJulga }));
  return el('section', { classe: 'bloco' }, [
    el('div', { classe: 'titulo-de-secao-linha' }, [
      el('h2', { texto: 'Declarado e indicado' }),
      quemJulga.botao,
    ]),
    quemJulga.balao,
    el('div', { classe: 'rolagem' }, [
      el('table', { classe: 'tabela-limpa tabela-dos-campos' }, [
        el('thead', {}, [el('tr', {}, [
          el('th', { scope: 'col', texto: 'Campo' }),
          el('th', { scope: 'col', texto: 'Declarado no documento' }),
          el('th', { scope: 'col', texto: 'Indicado pela base normativa' }),
        ])]),
        el('tbody', {}, linhas),
      ]),
    ]),
  ]);
}

function blocoDasDescricoes(descricoes) {
  const comoLer = botaoDeInformacao('Como ler as duas descricoes', el('p', { texto: descricoes.comoLer }));
  return el('section', { classe: 'bloco' }, [
    el('div', { classe: 'titulo-de-secao-linha' }, [el('h2', { texto: 'Descricao do produto' }), comoLer.botao]),
    comoLer.balao,
    el('div', { classe: 'descricoes' }, [
      cartaoDeDescricao('na nota', descricoes.naNota, descricoes.motivoSemDescricaoNaNota),
      cartaoDeDescricao('no catalogo, para o NCM declarado', descricoes.noCatalogo,
        descricoes.motivoSemDescricaoNoCatalogo),
    ]),
  ]);
}

function cartaoDeDescricao(titulo, texto, motivo) {
  if (texto) {
    return el('div', { classe: 'cartao-descricao' }, [
      el('h3', { texto: titulo }),
      el('p', { texto }),
    ]);
  }
  const porque = botaoDeInformacao('Por que nao ha descricao', el('p', {
    texto: motivo || 'ausente, e a resposta nao disse por que',
  }));
  porque.botao.textContent = '?';
  return el('div', { classe: 'cartao-descricao' }, [
    el('h3', { texto: titulo }),
    el('span', { classe: 'valor-com-info' }, [el('span', { classe: 'ausente', texto: 'nao disponivel' }), porque.botao]),
    porque.balao,
  ]);
}

function blocoDoTratamento(tratamento) {
  const familias = new Map();
  for (const doTributo of tratamento.porTributo) {
    if (!familias.has(doTributo.familia)) {
      familias.set(doTributo.familia, []);
    }
    familias.get(doTributo.familia).push(doTributo);
  }

  const quadros = [];
  for (const [familia, tributos] of familias) {
    quadros.push(el('div', { classe: 'quadro-tributo' }, [
      el('h4', { texto: familia }),
      ...tributos.map(quadroDeUmTributo),
    ]));
  }

  const sobreAsParcelas = botaoDeInformacao('Sobre as parcelas do IBS', el('p', {
    texto: 'As parcelas estadual e municipal do IBS aparecem separadas, cada uma com a propria '
      + 'vigencia e a propria fonte. O sistema nao as soma: somar produziria um percentual que '
      + 'nenhuma linha da carga declara.',
  }));

  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Tratamento indicado pela base normativa' }),
    el('p', { classe: 'coordenadas' }, [
      'Carga ',
      el('span', { classe: 'mono', texto: tratamento.versaoDoCatalogo }),
      ' · data de emissao ',
      el('span', { classe: 'mono', texto: data(tratamento.dataDeReferencia) }),
    ]),
    el('div', { classe: 'base-normativa-grade' }, [
      el('div', { classe: 'celula-normativa' }, [
        el('h3', { texto: 'NCM declarado' }),
        leituraDoCatalogo(tratamento.descricaoDoNcm, (registros) =>
          el('ul', { classe: 'lista-catalogo' }, registros.map((registro) => el('li', {}, [
            el('span', { classe: 'mono', texto: registro.ncm }),
            ' — ',
            el('span', { texto: registro.descricao }),
            referencia(registro.referencia),
          ])))),
        el('h3', { texto: 'Enquadramento' }),
        leituraDoCatalogo(tratamento.enquadramentos, (registros) =>
          el('ul', { classe: 'lista-catalogo' }, registros.map((registro) => el('li', {}, [
            el('strong', { texto: registro.anexo }),
            ' — ',
            el('span', { texto: registro.tipoDeTratamento }),
            referencia(registro.referencia),
          ])))),
      ]),
      el('div', { classe: 'celula-normativa' }, [
        el('h3', { texto: 'Classificacao tributaria declarada' }),
        leituraDoCatalogo(tratamento.classificacao, (registros) =>
          el('div', {}, registros.map(quadroDaClassificacao))),
      ]),
      el('div', { classe: 'celula-normativa' }, [
        el('div', { classe: 'titulo-de-secao-linha' }, [
          el('h3', { texto: 'Aliquotas vigentes na data' }),
          sobreAsParcelas.botao,
        ]),
        sobreAsParcelas.balao,
        el('div', { classe: 'tributos' }, quadros),
      ]),
    ]),
  ]);
}

function quadroDeUmTributo(doTributo) {
  return el('div', { classe: 'tributo' }, [
    el('p', { classe: 'tributo-rotulo', texto: doTributo.rotulo }),
    leituraDoCatalogo(doTributo.aliquotas, (aliquotas) =>
      el('ul', { classe: 'lista-catalogo' }, aliquotas.map((aliquota) => el('li', {}, [
        el('span', { classe: 'mono percentual', texto: aliquota.percentual }),
        ' — ',
        el('span', { texto: aliquota.abrangencia }),
        referencia(aliquota.referencia),
      ])))),
  ]);
}

function quadroDaClassificacao(classificacao) {
  const par = (chave, conteudo, classe) => el('div', { classe: 'meta-par' }, [
    el('dt', { texto: chave }),
    el('dd', { classe: classe || null }, [].concat(conteudo)),
  ]);
  return el('dl', { classe: 'metadados metadados-classificacao' }, [
    par('codigo', classificacao.codigo, 'mono'),
    par('CST admitidos', classificacao.cstsAdmitidos.length
      ? el('span', { classe: 'mono', texto: classificacao.cstsAdmitidos.join(', ') })
      : valorOuMotivo(null, classificacao.motivoSemCstAdmitido)),
    par('beneficio declarado', classificacao.indicadorDeBeneficio ? 'sim' : 'nao'),
    par('reducao', valorOuMotivo(classificacao.percentualReducao, classificacao.motivoSemReducao)),
    par('dispositivo citado pela fonte', classificacao.dispositivoLegal),
    par('campos condicionados', classificacao.camposObrigatoriosCondicionados.length
      ? el('span', { texto: classificacao.camposObrigatoriosCondicionados.join(', ') })
      : valorOuMotivo(null, classificacao.motivoSemCampoCondicionado)),
    par('vigencia e fonte', referencia(classificacao.referencia)),
  ]);
}

function referencia(ref) {
  if (!ref) {
    return el('span', { classe: 'ausente', texto: 'a resposta nao trouxe a referencia' });
  }
  const fim = ref.vigenciaFim
    ? data(ref.vigenciaFim)
    : (ref.motivoDaVigenciaSemFim || 'sem fim declarado');
  return el('p', { classe: 'referencia' }, [
    el('span', { texto: 'de ' + data(ref.vigenciaInicio) + ' ate ' }),
    ref.vigenciaFim
      ? el('span', { texto: fim })
      : el('span', { classe: 'ausente', texto: fim }),
    el('span', { texto: ' — fonte: ' }),
    el('span', { texto: ref.fonteNormativa }),
  ]);
}

function blocoDosPassos(passos) {
  return el('section', { classe: 'bloco' }, [
    el('h2', { texto: 'Por que este resultado' }),
    el('ol', { classe: 'passos' }, passos.map(passo)),
  ]);
}

function passo(item) {
  const cabeca = el('span', { classe: 'passo-cabeca' }, [
    rotuloDaRegra(item),
    item.regraVersao ? el('span', { classe: 'versao-da-regra', texto: 'v' + item.regraVersao }) : null,
    selo(item.estado, item.rotuloDoEstado, item.explicacaoDoEstado),
  ]);
  if (item.explicacao.tipo === 'DERIVACAO') {
    return el('li', { classe: 'passo' }, [cabeca]);
  }
  return el('li', { classe: 'passo' }, [
    recolhivel(cabeca, [explicacao(item.explicacao)], item.recolhidaPorPadrao),
  ]);
}

function esperado(evidencia) {
  if (evidencia.valorEsperado !== null && evidencia.valorEsperado !== undefined && evidencia.valorEsperado !== '') {
    return el('span', { texto: String(evidencia.valorEsperado) });
  }
  const porque = botaoDeInformacao('Por que nao ha valor esperado', el('p', {
    texto: evidencia.motivoDoEsperadoAusente || 'ausente, e a resposta nao disse por que',
  }));
  porque.botao.textContent = '?';
  return [
    el('span', { classe: 'valor-com-info' }, [
      el('span', { classe: 'ausente', 'aria-hidden': 'true', texto: '—' }),
      el('span', { classe: 'so-leitor-de-tela', texto: 'sem valor esperado' }),
      porque.botao,
    ]),
    porque.balao,
  ];
}

function explicacao(dados) {
  if (dados.tipo === 'APONTAMENTO') {
    return el('div', { classe: 'explicacao' }, [
      el('p', { classe: 'valor-em-risco' }, [
        el('span', { classe: 'valor-em-risco-rotulo', texto: 'Valor em risco' }),
        dados.valorEmRisco !== null && dados.valorEmRisco !== undefined && dados.valorEmRisco !== ''
          ? el('strong', { classe: 'valor-em-risco-numero', texto: String(dados.valorEmRisco) })
          : valorOuMotivo(null, dados.motivoDoValorAusente),
      ]),
      el('div', { classe: 'rolagem' }, [
        el('table', { classe: 'tabela-limpa tabela-evidencias' }, [
          el('thead', {}, [el('tr', {}, [
            el('th', { scope: 'col', texto: 'Campo examinado' }),
            el('th', { scope: 'col', texto: 'Encontrado' }),
            el('th', { scope: 'col', texto: 'Esperado' }),
            el('th', { scope: 'col', texto: 'De onde saiu' }),
          ])]),
          el('tbody', {}, dados.evidencias.map((evidencia) => el('tr', {}, [
            el('td', { texto: evidencia.campoAnalisado }),
            el('td', {}, [
              valorOuMotivo(evidencia.valorEncontrado, evidencia.motivoDoEncontradoAusente),
            ]),
            el('td', {}, [].concat(esperado(evidencia))),
            el('td', { classe: 'origem', texto: evidencia.origem }),
          ]))),
        ]),
      ]),
      el('p', { classe: 'fundamento' }, [
        el('strong', { texto: 'Fundamento: ' }),
        el('span', { texto: dados.fundamentacao.fonteNormativa }),
      ]),
      referencia(dados.fundamentacao),
    ]);
  }
  return el('p', { classe: 'explicacao texto-normativo', texto: dados.texto });
}
