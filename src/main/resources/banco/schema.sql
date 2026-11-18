CREATE TABLE IF NOT EXISTS produtos (
 codigo TEXT PRIMARY KEY,
 nome TEXT NOT NULL,
 preco_centavos INTEGER NOT NULL CHECK(preco_centavos > 0),
 estoque INTEGER NOT NULL CHECK(estoque >= 0)
);
CREATE TABLE IF NOT EXISTS clientes (
 id INTEGER PRIMARY KEY,
 identificacao TEXT NOT NULL UNIQUE,
 nome TEXT NOT NULL,
 email TEXT NOT NULL,
 tipo TEXT NOT NULL CHECK(tipo IN ('Padrão', 'VIP'))
);
CREATE TABLE IF NOT EXISTS vendas (
 id TEXT PRIMARY KEY,
 cliente_id INTEGER NOT NULL REFERENCES clientes(id),
 data_hora TEXT NOT NULL,
 subtotal_centavos INTEGER NOT NULL CHECK(subtotal_centavos > 0),
 desconto_centavos INTEGER NOT NULL CHECK(desconto_centavos >= 0),
 total_centavos INTEGER NOT NULL CHECK(total_centavos > 0),
 estado TEXT NOT NULL CHECK(estado = 'CONCLUIDO'),
 recibo TEXT NOT NULL,
 CHECK(total_centavos = subtotal_centavos - desconto_centavos)
);
CREATE TABLE IF NOT EXISTS itens_venda (
 venda_id TEXT NOT NULL REFERENCES vendas(id),
 produto_codigo TEXT NOT NULL REFERENCES produtos(codigo),
 produto_nome TEXT NOT NULL,
 quantidade INTEGER NOT NULL CHECK(quantidade > 0),
 preco_unitario_centavos INTEGER NOT NULL CHECK(preco_unitario_centavos > 0),
 subtotal_centavos INTEGER NOT NULL,
 PRIMARY KEY(venda_id, produto_codigo),
 CHECK(subtotal_centavos = quantidade * preco_unitario_centavos)
);
CREATE TABLE IF NOT EXISTS pagamentos (
 venda_id TEXT PRIMARY KEY REFERENCES vendas(id),
 tipo TEXT NOT NULL CHECK(tipo IN ('pix', 'credito', 'debito')),
 parcelas INTEGER NOT NULL CHECK(parcelas BETWEEN 1 AND 12),
 valor_centavos INTEGER NOT NULL CHECK(valor_centavos > 0),
 autorizacao TEXT NOT NULL UNIQUE,
 descricao TEXT NOT NULL,
 CHECK(tipo = 'credito' OR parcelas = 1)
);
CREATE TABLE IF NOT EXISTS enderecos_entrega (
 venda_id TEXT PRIMARY KEY REFERENCES vendas(id),
 cep TEXT NOT NULL,
 logradouro TEXT NOT NULL,
 numero TEXT NOT NULL,
 complemento TEXT NOT NULL,
 bairro TEXT NOT NULL,
 cidade TEXT NOT NULL,
 uf TEXT NOT NULL CHECK(length(uf) = 2)
);
CREATE TABLE IF NOT EXISTS cartoes (
 id TEXT PRIMARY KEY,
 cliente_id INTEGER NOT NULL REFERENCES clientes(id),
 titular TEXT NOT NULL,
 final_numero TEXT NOT NULL CHECK(final_numero GLOB '[0-9][0-9][0-9][0-9]'),
 validade TEXT NOT NULL,
 UNIQUE(cliente_id, titular, final_numero, validade)
);
CREATE TABLE IF NOT EXISTS assinaturas_vip (
 identificacao TEXT PRIMARY KEY NOT NULL,
 nome TEXT NOT NULL,
 email TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS mensalidades_vip (
 id INTEGER PRIMARY KEY,
 assinante TEXT NOT NULL REFERENCES assinaturas_vip(identificacao),
 autorizacao TEXT NOT NULL UNIQUE,
 valor_centavos INTEGER NOT NULL CHECK(valor_centavos > 0),
 pago_em TEXT NOT NULL,
 valido_ate TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS mensalidades_por_assinante ON mensalidades_vip(assinante, id DESC);
