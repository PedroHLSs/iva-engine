import { el, trocar } from '../../dom.js';
import { dataHora, inteiro } from '../../formato.js';
import { botaoDeInformacao } from '../../painel.js';
import { escrever, ler, quemEsta } from '../../sessao.js';
import { falha } from '../pecas.js';
import { enderecoDaCarga } from '../roteador.js';

export const ARQUIVOS_DA_CARGA = [
  'classificacao-tributaria.csv',
  'registro-ncm.csv',
  'item-anexo.csv',
  'aliquota-vigente.csv',
  'cobertura.csv',
];

export const ARQUIVO_OPCIONAL = 'anexos-declarados.csv';

const TABELA_DO_ARQUIVO = {
  'classificacao-tributaria.csv': 'CLASSIFICACAO_TRIBUTARIA',
  'registro-ncm.csv': 'NCM',
  'item-anexo.csv': 'ITEM_ANEXO',
  'aliquota-vigente.csv': 'ALIQUOTA',
  'cobertura.csv': 'COBERTURA',
  'anexos-declarados.csv': 'ANEXO_DECLARADO',
};

export async function desenhar(raiz) {
  const [cargas, usuario] = await Promise.all([ler('/api/cargas'), quemEsta()]);
  const podeAdministrar = usuario && usuario.permissoes.administrar;

  const info = botaoDeInformacao('Como as cargas sao usadas', [
    el('p', {
      texto: 'Cada analise usa a carga mais recente e fica presa a ela: reaberta depois, mostra a '
        + 'base normativa daquela carga, e nao a de hoje. Por isso carga ja usada por uma analise '
        + 'esta selada — editar cria uma versao nova, e excluir e recusado.',
    }),
  ]);

  trocar(raiz, [
    el('div', { classe: 'tela-cargas' }, [
      el('header', { classe: 'cg-cabecalho' }, [
        el('div', { classe: 'cg-titulo' }, [
          el('h1', { texto: 'Cargas de catalogo' }),
          info.botao,
        ]),
        info.balao,
      ]),
      tabela(cargas),
      podeAdministrar ? formularioDeImportacao(cargas) : el('p', {
        classe: 'cg-nota', texto: 'Importar, editar e excluir carga e do perfil de administrador.',
      }),
    ]),
  ]);
}

function uso(carga) {
  return el('span', { classe: 'cg-uso' }, [
    el('span', {
      classe: 'cg-selo ' + (carga.selada ? 'selada' : 'rascunho'),
      texto: carga.selada ? 'selada' : 'rascunho',
    }),
    el('span', {
      classe: 'cg-analises',
      texto: carga.selada ? 'usada por ' + inteiro(carga.analisesQueUsam) + ' analise(s)' : 'nenhuma analise',
    }),
  ]);
}

function origem(carga) {
  if (carga.derivadaDe) {
    return el('span', {}, ['editada a partir de ', el('span', { classe: 'mono', texto: carga.derivadaDe })]);
  }
  return el('span', { classe: 'cg-discreto', title: carga.motivoDaOrigemAusente }, [
    'carga importada',
    el('span', { classe: 'so-leitor-de-tela', texto: ' — ' + carga.motivoDaOrigemAusente }),
  ]);
}

function tabela(cargas) {
  if (cargas.length === 0) {
    return el('p', { classe: 'cg-vazio', texto: 'Nenhuma carga foi importada ainda.' });
  }
  const linhas = cargas.map((carga) => el('tr', {}, [
    el('td', {}, [el('a', { href: enderecoDaCarga(carga.versao), classe: 'mono cg-versao', texto: carga.versao })]),
    el('td', { texto: dataHora(carga.importadoEm) }),
    el('td', {}, [uso(carga)]),
    el('td', {}, [origem(carga)]),
    el('td', {}, [carga.maisRecente
      ? el('span', { classe: 'cg-selo proxima', texto: 'usada na proxima analise' })
      : null]),
  ]));
  return el('div', { classe: 'cg-cartao cg-cartao-tabela' }, [
    el('div', { classe: 'cg-rolagem' }, [
      el('table', { classe: 'data-table' }, [
        el('thead', {}, [el('tr', {}, [
          el('th', { scope: 'col', texto: 'Versao' }), el('th', { scope: 'col', texto: 'Importada em' }),
          el('th', { scope: 'col', texto: 'Uso' }), el('th', { scope: 'col', texto: 'Origem' }),
          el('th', { scope: 'col', texto: 'Proxima analise' }),
        ])]),
        el('tbody', {}, linhas),
      ]),
    ]),
  ]);
}

export function tabelaDeRecusas(falhaDaCarga) {
  const recusadas = falhaDaCarga.corpo && falhaDaCarga.corpo.recusadas;
  if (!recusadas) {
    return falha(falhaDaCarga);
  }
  return el('div', { classe: 'aviso erro' }, [
    el('strong', { texto: 'A carga foi recusada inteira. Nada foi gravado. ' }),
    'Corrija todas as linhas abaixo e envie de novo.',
    el('div', { classe: 'rolagem' }, [
      el('table', { classe: 'tabela' }, [
        el('thead', {}, [el('tr', {}, [
          el('th', { texto: 'arquivo' }), el('th', { texto: 'linha' }), el('th', { texto: 'coluna' }),
          el('th', { texto: 'valor' }), el('th', { texto: 'motivo' }),
        ])]),
        el('tbody', {}, recusadas.map((recusa) => el('tr', {}, [
          el('td', { classe: 'mono', texto: recusa.arquivo }),
          el('td', { texto: recusa.linha === null ? 'o arquivo inteiro' : String(recusa.linha) }),
          el('td', { classe: 'mono', texto: recusa.coluna === null ? '—' : recusa.coluna }),
          el('td', {
            classe: 'mono',
            texto: recusa.valor === null ? '—' : (recusa.valor === '' ? '(em branco)' : recusa.valor),
          }),
          el('td', { texto: recusa.motivo }),
        ]))),
      ]),
    ]),
  ]);
}

function escolhidos(lista) {
  if (lista.length === 0) {
    return 'Nenhum arquivo escolhido';
  }
  return inteiro(lista.length) + ' arquivo(s): ' + Array.from(lista, (arquivo) => arquivo.name).join(', ');
}

function instantesDaOrigem(origem) {
  return 'importada em ' + dataHora(origem.importadoEm) + '; '
    + (origem.alteradaEm ? 'alterada em ' + dataHora(origem.alteradaEm) : 'nunca alterada');
}

function planoDoEnvio(lista, origem) {
  const nomes = Array.from(lista, (arquivo) => arquivo.name);
  const completo = ARQUIVOS_DA_CARGA.every((nome) => nomes.includes(nome));
  const enviadas = Object.keys(TABELA_DO_ARQUIVO).filter((nome) => nomes.includes(nome));
  const herdadas = Object.keys(TABELA_DO_ARQUIVO).filter((nome) => !nomes.includes(nome));
  return { completo, parcial: !completo && origem !== null && nomes.length > 0, enviadas, herdadas };
}

function formularioDeImportacao(cargas) {
  const resultado = el('div', { role: 'status' });
  const vista = { origem: cargas.find((carga) => carga.maisRecente) || null };
  const plano = el('p', { classe: 'cg-ajuda', id: 'plano-da-importacao' });
  const botao = el('button', { type: 'submit', classe: 'cg-botao', texto: 'Importar' });
  const versao = el('input', {
    type: 'text', id: 'versao-nova', autocomplete: 'off', 'aria-describedby': 'versao-nova-ajuda',
  });
  const arquivos = el('input', {
    type: 'file', id: 'arquivos-da-carga', multiple: 'multiple', accept: '.csv',
    classe: 'cg-arquivo-nativo', 'aria-describedby': 'arquivos-escolhidos plano-da-importacao',
  });
  const nomes = el('span', { classe: 'cg-escolhidos', id: 'arquivos-escolhidos', texto: escolhidos([]) });

  function mostrarPlano() {
    nomes.textContent = escolhidos(arquivos.files);
    const envio = planoDoEnvio(arquivos.files, vista.origem);
    if (arquivos.files.length === 0) {
      plano.textContent = '';
      botao.textContent = 'Importar';
    } else if (envio.completo) {
      plano.textContent = 'Importacao completa: nenhuma tabela vira de '
        + (vista.origem ? '"' + vista.origem.versao + '"' : 'outra carga') + '. O ' + ARQUIVO_OPCIONAL
        + ' nunca e herdado numa importacao completa: se nao vier, a carga nova fica sem lista de anexos.';
      botao.textContent = 'Importar';
    } else if (envio.parcial) {
      plano.textContent = 'Serao substituidas: ' + envio.enviadas.map((nome) => TABELA_DO_ARQUIVO[nome]).join(', ')
        + ' · Virao de "' + vista.origem.versao + '", com a natureza que tem la: '
        + envio.herdadas.map((nome) => TABELA_DO_ARQUIVO[nome]).join(', ') + '.';
      botao.textContent = 'Importar criando "' + (versao.value.trim() || '(versao)') + '" a partir de "'
        + vista.origem.versao + '"';
    } else {
      plano.textContent = 'Faltam arquivos obrigatorios, e ainda nao ha carga de onde herda-los.';
      botao.textContent = 'Importar';
    }
  }

  arquivos.addEventListener('change', mostrarPlano);
  versao.addEventListener('input', mostrarPlano);

  const formulario = el('form', { classe: 'cg-cartao cg-formulario', 'data-exige': 'administrar' }, [
    el('h2', { texto: 'Importar carga nova' }),
    el('div', { classe: 'cg-campo' }, [
      el('label', { for: 'versao-nova', texto: 'Versao' }),
      versao,
      el('p', { classe: 'cg-ajuda', id: 'versao-nova-ajuda', texto: 'O nome que cada analise vai registrar.' }),
    ]),
    el('div', { classe: 'cg-campo' }, [
      el('span', { classe: 'cg-rotulo', texto: 'Arquivos CSV' }),
      el('div', { classe: 'cg-seletor' }, [
        arquivos,
        el('label', { for: 'arquivos-da-carga', classe: 'cg-botao', texto: 'Escolher arquivos' }),
        nomes,
      ]),
    ]),
    plano,
    el('div', { classe: 'cg-acoes' }, [botao]),
    resultado,
  ]);

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    trocar(resultado, [el('p', { classe: 'estado-do-envio', texto: 'Lendo os arquivos...' })]);
    const envio = new FormData();
    envio.append('versao', versao.value);
    if (planoDoEnvio(arquivos.files, vista.origem).parcial) {
      envio.append('cargaDeOrigemEsperada', vista.origem.versao);
      envio.append('origemImportadaEmEsperada', vista.origem.importadoEm);
      envio.append('origemAlteradaEmEsperada', vista.origem.alteradaEm || '');
    }
    for (const arquivo of arquivos.files) {
      envio.append('arquivos', arquivo, arquivo.name);
    }
    try {
      const importada = await escrever('POST', '/api/cargas', envio);
      trocar(resultado, [el('div', { classe: 'aviso' }, [
        el('strong', { texto: 'Carga "' + importada.versao + '" importada: ' }),
        inteiro(importada.total) + ' registros. ',
        importada.aviso + ' ',
        el('a', { href: enderecoDaCarga(importada.versao), texto: 'Abrir' }),
      ])]);
    } catch (recusa) {
      const corpo = recusa.corpo || {};
      if ((recusa.codigo === 'ORIGEM_DESATUALIZADA' || recusa.codigo === 'ORIGEM_NAO_INFORMADA') && corpo.origemAtual) {
        vista.origem = corpo.origemAtual;
        mostrarPlano();
        trocar(resultado, [el('div', { classe: 'aviso erro' }, [
          el('strong', { texto: 'Nada foi gravado. ' }), recusa.message,
          ' A carga mais recente agora e "' + vista.origem.versao + '" (' + instantesDaOrigem(vista.origem)
            + '). Confira as tabelas que virao dela e envie de novo.',
        ])]);
      } else {
        trocar(resultado, [tabelaDeRecusas(recusa)]);
      }
    }
  });
  return formulario;
}
