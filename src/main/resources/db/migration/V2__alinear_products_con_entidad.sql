-- Corrige diferencias que ddl-auto=update dejó en products:
-- 1. created_by_user_id ya no existe en la entidad Product (columna huérfana).
-- 2. unit nunca recibió el CHECK con los valores del enum Unit.

ALTER TABLE products DROP COLUMN created_by_user_id;

ALTER TABLE products
    ADD CONSTRAINT products_unit_check CHECK (unit IN ('UNID', 'KG', 'G', 'L', 'ML', 'CAJA', 'M'));
