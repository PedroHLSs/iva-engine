import { el, trocar } from '../../dom.js';
import { dataHora } from '../../formato.js';
import { botaoDeInformacao } from '../../painel.js';
import { escrever, ler } from '../../sessao.js';
import { falha } from '../pecas.js';

const PERFIS = [
  { valor: 'ADMINISTRADOR', rotulo: 'Administrador' },
  { valor: 'FISCAL', rotulo: 'Fiscal' },
  { valor: 'CONSULTA', rotulo: 'Consulta' },
];

export async function desenhar(raiz) {
  const mensagem = el('div', { role: 'status', classe: 'us-mensagem' });
  const lista = el('div', {});

  async function recarregar() {
    const usuarios = await ler('/api/usuarios');
    trocar(lista, [tabela(usuarios, mensagem, recarregar)]);
  }

  const info = botaoDeInformacao('O que cada perfil pode fazer', [
    el('p', {
      texto: 'Administrador gerencia usuarios e cargas de catalogo e faz tudo o que o fiscal faz. '
        + 'Fiscal envia notas, consulta analises e registra tratativa. Consulta so le.',
    }),
  ]);

  trocar(raiz, [
    el('div', { classe: 'tela-usuarios' }, [
      el('header', { classe: 'us-cabecalho' }, [
        el('div', { classe: 'us-titulo' }, [el('h1', { texto: 'Usuarios' }), info.botao]),
        info.balao,
      ]),
      mensagem,
      lista,
      formularioDeCriacao(mensagem, recarregar),
    ]),
  ]);
  await recarregar();
}

function situacao(usuario) {
  if (usuario.ativo) {
    return el('span', { classe: 'us-selo ativo', texto: 'ativo' });
  }
  return el('span', { classe: 'us-situacao' }, [
    el('span', { classe: 'us-selo desativado', texto: 'desativado' }),
    el('span', { classe: 'us-data', texto: 'em ' + dataHora(usuario.desativadoEm) }),
  ]);
}

function tabela(usuarios, mensagem, recarregar) {
  const linhas = usuarios.map((usuario) => {
    return el('tr', {}, [
      el('td', { classe: 'mono', texto: usuario.login }),
      el('td', { texto: usuario.nome }),
      el('td', {}, [el('span', { classe: 'us-selo perfil', texto: rotuloDoPerfil(usuario.perfil) })]),
      el('td', {}, [situacao(usuario)]),
      el('td', { classe: 'us-coluna-acoes' }, [el('div', { classe: 'us-acoes-da-linha' }, [
        botaoDeIcone(usuario.ativo ? 'desativar' : 'reativar',
          usuario.ativo ? 'Desativar usuario' : 'Reativar usuario', usuario.login,
          () => alterar(usuario, { ativo: !usuario.ativo }, mensagem, recarregar)),
        botaoDeIcone('perfil', 'Alterar perfil', usuario.login,
          () => trocar(mensagem, [formularioDePerfil(usuario, mensagem, recarregar)])),
        botaoDeIcone('senha', 'Redefinir senha', usuario.login,
          () => trocar(mensagem, [formularioDeSenha(usuario, mensagem, recarregar)])),
        botaoDeIcone('excluir', 'Excluir usuario', usuario.login,
          () => excluir(usuario, mensagem, recarregar), 'perigo'),
      ])]),
    ]);
  });

  return el('div', { classe: 'us-cartao us-cartao-tabela' }, [
    el('div', { classe: 'us-rolagem' }, [
      el('table', { classe: 'data-table' }, [
        el('thead', {}, [el('tr', {}, [
          el('th', { scope: 'col', texto: 'Login' }), el('th', { scope: 'col', texto: 'Nome' }),
          el('th', { scope: 'col', texto: 'Perfil' }), el('th', { scope: 'col', texto: 'Situacao' }),
          el('th', { scope: 'col', classe: 'us-coluna-acoes', texto: 'Acoes' }),
        ])]),
        el('tbody', {}, linhas),
      ]),
    ]),
  ]);
}

function campo(id, rotulo, controle, ajuda) {
  if (ajuda) {
    controle.setAttribute('aria-describedby', id + '-ajuda');
  }
  return el('div', { classe: 'us-campo' }, [
    el('label', { for: id, texto: rotulo }),
    controle,
    ajuda ? el('p', { classe: 'us-ajuda', id: id + '-ajuda', texto: ajuda }) : null,
  ]);
}

const ICONES = {
  desativar: [['circle', { cx: 12, cy: 12, r: 9 }], ['line', { x1: 5.6, y1: 5.6, x2: 18.4, y2: 18.4 }]],
  reativar: [['path', { d: 'M12 3v9' }], ['path', { d: 'M18.4 6.6a9 9 0 1 1-12.8 0' }]],
  perfil: [['path', { d: 'M12 20h9' }], ['path', { d: 'M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z' }]],
  senha: [
    ['circle', { cx: 7.5, cy: 15.5, r: 4.5 }], ['path', { d: 'm10.7 12.3 9.8-9.8' }],
    ['path', { d: 'm16 7 3 3' }], ['path', { d: 'm19 4 2 2' }],
  ],
  excluir: [
    ['path', { d: 'M3 6h18' }], ['path', { d: 'M8 6V4h8v2' }], ['path', { d: 'M19 6l-1 14H6L5 6' }],
    ['line', { x1: 10, y1: 11, x2: 10, y2: 17 }], ['line', { x1: 14, y1: 11, x2: 14, y2: 17 }],
  ],
};

function icone(nome) {
  const NS = 'http://www.w3.org/2000/svg';
  const svg = document.createElementNS(NS, 'svg');
  const atributos = {
    viewBox: '0 0 24 24', width: 18, height: 18, fill: 'none', stroke: 'currentColor',
    'stroke-width': 2, 'stroke-linecap': 'round', 'stroke-linejoin': 'round',
    'aria-hidden': 'true', focusable: 'false',
  };
  for (const [chave, valor] of Object.entries(atributos)) svg.setAttribute(chave, String(valor));
  for (const [tag, tracos] of ICONES[nome]) {
    const parte = document.createElementNS(NS, tag);
    for (const [chave, valor] of Object.entries(tracos)) parte.setAttribute(chave, String(valor));
    svg.appendChild(parte);
  }
  return svg;
}

function botaoDeIcone(nome, acao, login, aoClicar, variante) {
  return el('button', {
    type: 'button', classe: 'us-icone' + (variante ? ' ' + variante : ''),
    title: acao, 'aria-label': acao + ' ' + login, aoClicar,
  }, [icone(nome)]);
}

function rotuloDoPerfil(valor) {
  const conhecido = PERFIS.find((opcao) => opcao.valor === valor);
  return conhecido ? conhecido.rotulo : String(valor);
}

function formularioDePerfil(usuario, mensagem, recarregar) {
  const perfil = el('select', { id: 'perfil-alterado' },
    PERFIS.map((opcao) => el('option', {
      value: opcao.valor, texto: opcao.rotulo, selected: opcao.valor === usuario.perfil ? 'selected' : null,
    })));
  const formulario = el('form', { classe: 'us-cartao us-formulario us-formulario-acao' }, [
    el('h2', {}, ['Alterar perfil de ', el('span', { classe: 'mono', texto: usuario.login })]),
    campo('perfil-alterado', 'Perfil', perfil, 'Perfil atual: ' + rotuloDoPerfil(usuario.perfil) + '.'),
    el('div', { classe: 'us-acoes' }, [
      el('button', { type: 'button', classe: 'us-botao', texto: 'Cancelar', aoClicar: () => trocar(mensagem, []) }),
      el('button', { type: 'submit', classe: 'us-botao', texto: 'Alterar' }),
    ]),
  ]);
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    alterar(usuario, { perfil: perfil.value }, mensagem, recarregar);
  });
  return formulario;
}

function formularioDeSenha(usuario, mensagem, recarregar) {
  const campoDaSenha = el('input', { type: 'password', id: 'senha-redefinida', autocomplete: 'new-password' });
  const formulario = el('form', { classe: 'us-cartao us-formulario us-formulario-acao' }, [
    el('h2', {}, ['Redefinir senha de ', el('span', { classe: 'mono', texto: usuario.login })]),
    campo('senha-redefinida', 'Senha nova', campoDaSenha,
      'Minimo de 12 caracteres. Entregue-a a pessoa por fora do sistema; ela pode troca-la depois de entrar.'),
    el('div', { classe: 'us-acoes' }, [
      el('button', { type: 'button', classe: 'us-botao', texto: 'Cancelar', aoClicar: () => trocar(mensagem, []) }),
      el('button', { type: 'submit', classe: 'us-botao', texto: 'Redefinir' }),
    ]),
  ]);
  formulario.addEventListener('submit', (evento) => {
    evento.preventDefault();
    const nova = campoDaSenha.value;
    campoDaSenha.value = '';
    alterar(usuario, { novaSenha: nova }, mensagem, recarregar);
  });
  return formulario;
}

async function alterar(usuario, mudanca, mensagem, recarregar) {
  trocar(mensagem, []);
  try {
    await escrever('PUT', '/api/usuarios/' + encodeURIComponent(usuario.id), mudanca);
    trocar(mensagem, [el('div', { classe: 'aviso', texto: 'Usuario ' + usuario.login + ' alterado.' })]);
  } catch (recusa) {
    trocar(mensagem, [falha(recusa)]);
  }
  await recarregar();
}

async function excluir(usuario, mensagem, recarregar) {
  if (!window.confirm('Excluir ' + usuario.login + '? Se ele ja registrou tratativa, sera desativado, '
    + 'e nao apagado.')) {
    return;
  }
  trocar(mensagem, []);
  try {
    const resultado = await escrever('DELETE', '/api/usuarios/' + encodeURIComponent(usuario.id));
    trocar(mensagem, [el('div', { classe: 'aviso' }, [
      el('strong', { texto: resultado.resultado === 'DESATIVADO' ? 'Desativado. ' : 'Removido. ' }),
      resultado.explicacao,
    ])]);
  } catch (recusa) {
    trocar(mensagem, [falha(recusa)]);
  }
  await recarregar();
}

function formularioDeCriacao(mensagem, recarregar) {
  const login = el('input', { type: 'text', id: 'novo-login', autocomplete: 'off' });
  const nome = el('input', { type: 'text', id: 'novo-nome', autocomplete: 'off' });
  const perfil = el('select', { id: 'novo-perfil' },
    PERFIS.map((opcao) => el('option', { value: opcao.valor, texto: opcao.rotulo })));
  const senha = el('input', { type: 'password', id: 'nova-senha', autocomplete: 'new-password' });

  const formulario = el('form', { classe: 'us-cartao us-formulario' }, [
    el('h2', { texto: 'Novo usuario' }),
    el('div', { classe: 'us-grade' }, [
      campo('novo-login', 'Login', login, 'Letras sem acento, numeros, ponto e hifen.'),
      campo('novo-nome', 'Nome', nome, 'Como aparece nas tratativas.'),
      campo('novo-perfil', 'Perfil', perfil, null),
      campo('nova-senha', 'Senha inicial', senha, 'Minimo de 12 caracteres.'),
    ]),
    el('div', { classe: 'us-acoes' }, [el('button', { type: 'submit', classe: 'us-botao', texto: 'Criar' })]),
  ]);

  formulario.addEventListener('submit', async (evento) => {
    evento.preventDefault();
    trocar(mensagem, []);
    try {
      await escrever('POST', '/api/usuarios', {
        login: login.value, nome: nome.value, perfil: perfil.value, senha: senha.value,
      });
      trocar(mensagem, [el('div', { classe: 'aviso', texto: 'Usuario ' + login.value + ' criado.' })]);
      login.value = '';
      nome.value = '';
    } catch (recusa) {
      trocar(mensagem, [falha(recusa)]);
    } finally {
      senha.value = '';
    }
    await recarregar();
  });
  return formulario;
}
