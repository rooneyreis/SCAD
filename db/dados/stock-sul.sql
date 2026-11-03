-- Stock inicial do armazém SUL (Maputo). Cenários de demonstração em docs/notas-relatorio.md.
INSERT INTO stock (armazem_id, produto_id, quantidade, minimo, maximo) VALUES
    ('SUL', 'ARROZ-25',    300, 100, 400),
    ('SUL', 'FARINHA-25',   70,  80, 300),  -- já abaixo do mínimo no arranque
    ('SUL', 'FEIJAO-10',   120,  60, 250),
    ('SUL', 'ACUCAR-10',    85,  80, 300),  -- "perto do mínimo" (85 <= 1,2 x 80)
    ('SUL', 'OLEO-5',      140,  60, 250),
    ('SUL', 'SABAO-BARRA', 350, 200, 800);
