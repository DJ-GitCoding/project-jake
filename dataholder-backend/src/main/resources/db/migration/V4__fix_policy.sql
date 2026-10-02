ALTER TABLE policy_redaction_rules 
  DROP CONSTRAINT IF EXISTS uk8558sgac6t31lkuhx66na8w9e;
ALTER TABLE policy_redaction_rules 
  ADD CONSTRAINT uk_policy_objecttype_fieldpath 
  UNIQUE (policy_expression_id, object_type, field_path);