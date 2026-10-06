export class FalhaDePedido extends Error {
  constructor(mensagem, codigo, situacao, corpo) {
    super(mensagem);
    this.name = 'FalhaDePedido';
    this.detalhe = null;
    this.codigo = codigo || null;
    this.situacao = situacao || 0;
    this.corpo = corpo || null;
  }
}

const SEM_SERVIDOR =
  'Nao foi possivel falar com o sistema. Ele sobe com o perfil "api" ativo e escuta so em '
  + '127.0.0.1.';

let tokenGuardado = null;
let usuarioGuardado;

export function enderecoDeEntrada() {
  const aqui = window.location.pathname.replace(/^.*\//, '') + window.location.hash;
  return 'index.html#/entrar?volta=' + encodeURIComponent(aqui || 'index.html#/');
}

export function irParaEntrada() {
  window.location.href = enderecoDeEntrada();
}

async function interpretar(resposta, caminho) {
  const texto = await resposta.text();
  let json = null;
  try {
    json = texto ? JSON.parse(texto) : null;
  } catch (naoEraJson) {
    json = null;
  }
  if (!resposta.ok) {
    const mensagem = json && json.mensagem
      ? json.mensagem
      : `O sistema respondeu ${resposta.status} para ${caminho}.`;
    throw new FalhaDePedido(mensagem, json && json.erro, resposta.status, json);
  }
  return json;
}

async function token() {
  if (tokenGuardado) {
    return tokenGuardado;
  }
  let resposta;
  try {
    resposta = await fetch('/api/sessao/csrf', { headers: { Accept: 'application/json' } });
  } catch (semRede) {
    throw new FalhaDePedido(SEM_SERVIDOR);
  }
  tokenGuardado = await interpretar(resposta, '/api/sessao/csrf');
  return tokenGuardado;
}

export async function escrever(metodo, caminho, corpo) {
  const credencial = await token();
  const cabecalhos = { Accept: 'application/json' };
  cabecalhos[credencial.cabecalho] = credencial.token;
  let conteudo;
  if (corpo instanceof FormData) {
    conteudo = corpo;
  } else if (corpo !== undefined && corpo !== null) {
    cabecalhos['Content-Type'] = 'application/json';
    conteudo = JSON.stringify(corpo);
  }

  let resposta;
  try {
    resposta = await fetch(caminho, { method: metodo, headers: cabecalhos, body: conteudo });
  } catch (semRede) {
    throw new FalhaDePedido(SEM_SERVIDOR);
  }
  if (resposta.status === 401 && caminho !== '/api/sessao') {
    usuarioGuardado = null;
    irParaEntrada();
  }
  if (resposta.status === 403) {
    tokenGuardado = null;
  }
  return interpretar(resposta, caminho);
}

export async function ler(caminho) {
  let resposta;
  try {
    resposta = await fetch(caminho, { headers: { Accept: 'application/json' } });
  } catch (semRede) {
    throw new FalhaDePedido(SEM_SERVIDOR);
  }
  if (resposta.status === 401) {
    usuarioGuardado = null;
    irParaEntrada();
  }
  return interpretar(resposta, caminho);
}

export async function quemEsta() {
  if (usuarioGuardado !== undefined) {
    return usuarioGuardado;
  }
  let resposta;
  try {
    resposta = await fetch('/api/sessao', { headers: { Accept: 'application/json' } });
  } catch (semRede) {
    throw new FalhaDePedido(SEM_SERVIDOR);
  }
  usuarioGuardado = resposta.status === 401 ? null : await interpretar(resposta, '/api/sessao');
  return usuarioGuardado;
}

export async function exigirSessao() {
  const usuario = await quemEsta();
  if (!usuario) {
    irParaEntrada();
  }
  return usuario;
}

export async function entrar(login, senha) {
  tokenGuardado = null;
  const usuario = await escrever('POST', '/api/sessao', { login, senha });
  usuarioGuardado = usuario;
  tokenGuardado = null;
  return usuario;
}

export async function sair() {
  try {
    await escrever('DELETE', '/api/sessao');
  } finally {
    usuarioGuardado = null;
    tokenGuardado = null;
  }
}

export function trocarSenha(senhaAtual, senhaNova) {
  return escrever('PUT', '/api/sessao/senha', { senhaAtual, senhaNova });
}

export function ajustarAoPerfil(usuario, raiz = document) {
  for (const elemento of raiz.querySelectorAll('[data-exige]')) {
    const permitido = usuario && usuario.permissoes && usuario.permissoes[elemento.dataset.exige];
    elemento.hidden = !permitido;
  }
}
