-- Stock inicial do armazém NORTE (Nampula). Cenários de demonstração em docs/notas-relatorio.md.
INSERT INTO stock (armazem_id, produto_id, quantidade, minimo, maximo) VALUES
    ('NORTE', 'ARROZ-25',    105, 100, 400),  -- "Pico simultâneo": uma saída de 10 deixa-o abaixo do mínimo
    ('NORTE', 'FARINHA-25',  200,  80, 300),
    ('NORTE', 'FEIJAO-10',   150,  60, 250),
    ('NORTE', 'ACUCAR-10',   180,  80, 300),
    ('NORTE', 'OLEO-5',      100,  60, 250),  -- saída de 45 -> repartição com sobra (prioridade 3 + excesso)
    ('NORTE', 'SABAO-BARRA', 400, 200, 800);
