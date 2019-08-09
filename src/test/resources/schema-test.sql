
-- -----------------------------------------------------
-- Schema SEBServer
-- -----------------------------------------------------

-- -----------------------------------------------------
-- Table `institution`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `institution` ;

CREATE TABLE IF NOT EXISTS `institution` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) NOT NULL,
  `url_suffix` VARCHAR(45) NULL,
  `logo_image` MEDIUMTEXT NULL,
  `theme_name` VARCHAR(45) NULL,
  `active` INT(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `name_UNIQUE` (`name` ASC))
;


-- -----------------------------------------------------
-- Table `lms_setup`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `lms_setup` ;

CREATE TABLE IF NOT EXISTS `lms_setup` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `lms_type` VARCHAR(45) NOT NULL,
  `lms_url` VARCHAR(255) NULL,
  `lms_clientname` VARCHAR(4000) NULL,
  `lms_clientsecret` VARCHAR(4000) NULL,
  `lms_rest_api_token` VARCHAR(4000) NULL,
  `active` INT(1) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `setupInstitutionRef_idx` (`institution_id` ASC),
  CONSTRAINT `setupInstitutionRef`
    FOREIGN KEY (`institution_id`)
    REFERENCES `institution` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `exam`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `exam` ;

CREATE TABLE IF NOT EXISTS `exam` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `lms_setup_id` BIGINT UNSIGNED NOT NULL,
  `external_id` VARCHAR(255) NOT NULL,
  `owner` VARCHAR(255) NOT NULL,
  `supporter` VARCHAR(4000) NULL COMMENT 'comma separated list of user_uuid',
  `type` VARCHAR(45) NOT NULL,
  `quit_password` VARCHAR(4000) NULL,
  `browser_keys` VARCHAR(4000) NULL,
  `active` INT(1) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `lms_setup_key_idx` (`lms_setup_id` ASC),
  INDEX `institution_key_idx` (`institution_id` ASC),
  CONSTRAINT `examLmsSetupRef`
    FOREIGN KEY (`lms_setup_id`)
    REFERENCES `lms_setup` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `examInstitutionRef`
    FOREIGN KEY (`institution_id`)
    REFERENCES `institution` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `client_connection`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `client_connection` ;

CREATE TABLE IF NOT EXISTS `client_connection` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `exam_id` BIGINT UNSIGNED NULL,
  `status` VARCHAR(45) NOT NULL,
  `connection_token` VARCHAR(255) NOT NULL,
  `exam_user_session_identifer` VARCHAR(255) NULL,
  `client_address` VARCHAR(45) NOT NULL,
  `virtual_client_address` VARCHAR(45) NULL,
  PRIMARY KEY (`id`),
  INDEX `connection_exam_ref_idx` (`exam_id` ASC),
  INDEX `clientConnectionInstitutionRef_idx` (`institution_id` ASC),
  CONSTRAINT `clientConnectionExamRef`
    FOREIGN KEY (`exam_id`)
    REFERENCES `exam` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `clientConnectionInstitutionRef`
    FOREIGN KEY (`institution_id`)
    REFERENCES `institution` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION);


-- -----------------------------------------------------
-- Table `client_event`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `client_event` ;

CREATE TABLE IF NOT EXISTS `client_event` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `connection_id` BIGINT UNSIGNED NOT NULL,
  `type` INT(2) UNSIGNED NOT NULL,
  `client_time` BIGINT UNSIGNED NOT NULL,
  `server_time` BIGINT NOT NULL,
  `numeric_value` DECIMAL(10,4) NULL,
  `text` VARCHAR(512) NULL,
  PRIMARY KEY (`id`),
  INDEX `eventConnectionRef_idx` (`connection_id` ASC),
  CONSTRAINT `eventConnectionRef`
    FOREIGN KEY (`connection_id`)
    REFERENCES `client_connection` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `indicator`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `indicator` ;

CREATE TABLE IF NOT EXISTS `indicator` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `exam_id` BIGINT UNSIGNED NOT NULL,
  `type` VARCHAR(45) NOT NULL,
  `name` VARCHAR(45) NOT NULL,
  `color` VARCHAR(45) NULL,
  INDEX `indicator_exam_idx` (`exam_id` ASC),
  PRIMARY KEY (`id`),
  CONSTRAINT `exam_ref`
    FOREIGN KEY (`exam_id`)
    REFERENCES `exam` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `configuration_node`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `configuration_node` ;

CREATE TABLE IF NOT EXISTS `configuration_node` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `template_id` BIGINT UNSIGNED NULL,
  `owner` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `description` VARCHAR(4000) NULL,
  `type` VARCHAR(45) NULL,
  `status` VARCHAR(45) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `configurationInstitutionRef_idx` (`institution_id` ASC),
  CONSTRAINT `configurationInstitutionRef`
    FOREIGN KEY (`institution_id`)
    REFERENCES `institution` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `configuration`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `configuration` ;

CREATE TABLE IF NOT EXISTS `configuration` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `configuration_node_id` BIGINT UNSIGNED NOT NULL,
  `version` VARCHAR(255) NULL,
  `version_date` DATETIME NULL,
  `followup` INT(1) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `configurationNodeRef_idx` (`configuration_node_id` ASC),
  INDEX `config_institution_ref_idx` (`institution_id` ASC),
  CONSTRAINT `configuration_node_ref`
    FOREIGN KEY (`configuration_node_id`)
    REFERENCES `configuration_node` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `config_institution_ref`
    FOREIGN KEY (`institution_id`)
    REFERENCES `institution` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `configuration_attribute`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `configuration_attribute` ;

CREATE TABLE IF NOT EXISTS `configuration_attribute` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(45) NOT NULL,
  `type` VARCHAR(45) NOT NULL,
  `parent_id` BIGINT UNSIGNED NULL,
  `resources` VARCHAR(255) NULL,
  `validator` VARCHAR(45) NULL,
  `dependencies` VARCHAR(255) NULL,
  `default_value` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  INDEX `parent_ref_idx` (`parent_id` ASC),
  CONSTRAINT `parent_ref`
    FOREIGN KEY (`parent_id`)
    REFERENCES `configuration_attribute` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `configuration_value`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `configuration_value` ;

CREATE TABLE IF NOT EXISTS `configuration_value` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `configuration_id` BIGINT UNSIGNED NOT NULL,
  `configuration_attribute_id` BIGINT UNSIGNED NOT NULL,
  `list_index` INT NOT NULL DEFAULT 0,
  `value` VARCHAR(20000) NULL,
  PRIMARY KEY (`id`),
  INDEX `configuration_value_ref_idx` (`configuration_id` ASC),
  INDEX `configuration_attribute_ref_idx` (`configuration_attribute_id` ASC),
  INDEX `configuration_value_institution_ref_idx` (`institution_id` ASC),
  CONSTRAINT `configuration_ref`
    FOREIGN KEY (`configuration_id`)
    REFERENCES `configuration` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `configuration_value_attribute_ref`
    FOREIGN KEY (`configuration_attribute_id`)
    REFERENCES `configuration_attribute` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `configuration_value_institution_ref`
    FOREIGN KEY (`institution_id`)
    REFERENCES `institution` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `view`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `view` ;

CREATE TABLE IF NOT EXISTS `view` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(255) NULL,
  `columns` INT NOT NULL,
  `position` INT NOT NULL,
  PRIMARY KEY (`id`))
;


-- -----------------------------------------------------
-- Table `orientation`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `orientation` ;

CREATE TABLE IF NOT EXISTS `orientation` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `config_attribute_id` BIGINT UNSIGNED NOT NULL,
  `template_id` BIGINT UNSIGNED NULL,
  `view_id` BIGINT UNSIGNED NOT NULL,
  `group_id` VARCHAR(45) NULL,
  `x_position` INT UNSIGNED NOT NULL DEFAULT 0,
  `y_position` INT UNSIGNED NOT NULL DEFAULT 0,
  `width` INT UNSIGNED NULL,
  `height` INT UNSIGNED NULL,
  `title` VARCHAR(45) NULL,
  PRIMARY KEY (`id`),
  INDEX `config_attribute_orientation_rev_idx` (`config_attribute_id` ASC),
  INDEX `orientation_view_ref_idx` (`view_id` ASC),
  CONSTRAINT `config_attribute_orientation_ref`
    FOREIGN KEY (`config_attribute_id`)
    REFERENCES `configuration_attribute` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `orientation_view_ref`
    FOREIGN KEY (`view_id`)
    REFERENCES `view` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `exam_configuration_map`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `exam_configuration_map` ;

CREATE TABLE IF NOT EXISTS `exam_configuration_map` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `exam_id` BIGINT UNSIGNED NOT NULL,
  `configuration_node_id` BIGINT UNSIGNED NOT NULL,
  `user_names` VARCHAR(4000) NULL,
  `encrypt_secret` VARCHAR(255) NULL,
  PRIMARY KEY (`id`),
  INDEX `exam_ref_idx` (`exam_id` ASC),
  INDEX `configuration_map_ref_idx` (`configuration_node_id` ASC),
  INDEX `exam_config_institution_ref_idx` (`institution_id` ASC),
  CONSTRAINT `exam_map_ref`
    FOREIGN KEY (`exam_id`)
    REFERENCES `exam` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `configuration_map_ref`
    FOREIGN KEY (`configuration_node_id`)
    REFERENCES `configuration_node` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `exam_config_institution_ref`
    FOREIGN KEY (`institution_id`)
    REFERENCES `institution` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `user`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `user` ;

CREATE TABLE IF NOT EXISTS `user` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `uuid` VARCHAR(255) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `username` VARCHAR(255) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `email` VARCHAR(255) NULL,
  `language` VARCHAR(45) NOT NULL,
  `timeZone` VARCHAR(45) NOT NULL,
  `active` INT(1) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `institutionRef_idx` (`institution_id` ASC),
  CONSTRAINT `userInstitutionRef`
    FOREIGN KEY (`institution_id`)
    REFERENCES `institution` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `user_role`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `user_role` ;

CREATE TABLE IF NOT EXISTS `user_role` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT UNSIGNED NOT NULL,
  `role_name` VARCHAR(45) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `user_ref_idx` (`user_id` ASC),
  CONSTRAINT `user_ref`
    FOREIGN KEY (`user_id`)
    REFERENCES `user` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `oauth_access_token`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oauth_access_token` ;

CREATE TABLE IF NOT EXISTS `oauth_access_token` (
  `token_id` VARCHAR(255) NULL,
  `token` BLOB NULL,
  `authentication_id` VARCHAR(255) NULL,
  `user_name` VARCHAR(255) NULL,
  `client_id` VARCHAR(255) NULL,
  `authentication` BLOB NULL,
  `refresh_token` VARCHAR(255) NULL,
  UNIQUE INDEX `authentication_id_UNIQUE` (`authentication_id` ASC))
;

-- -----------------------------------------------------
-- Table `oauth_refresh_token`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oauth_refresh_token` ;

CREATE TABLE IF NOT EXISTS `oauth_refresh_token` (
  `token_id` VARCHAR(255) NULL,
  `token` BLOB NULL,
  `authentication` BLOB NULL)
;


-- -----------------------------------------------------
-- Table `threshold`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `threshold` ;

CREATE TABLE IF NOT EXISTS `threshold` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `indicator_id` BIGINT UNSIGNED NOT NULL,
  `value` DECIMAL(10,4) NOT NULL,
  `color` VARCHAR(45) NULL,
  PRIMARY KEY (`id`),
  INDEX `indicator_threshold_id_idx` (`indicator_id` ASC),
  CONSTRAINT `indicator_threshold_id`
    FOREIGN KEY (`indicator_id`)
    REFERENCES `indicator` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `user_activity_log`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `user_activity_log` ;

CREATE TABLE IF NOT EXISTS `user_activity_log` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_uuid` VARCHAR(255) NOT NULL,
  `timestamp` BIGINT NOT NULL,
  `activity_type` VARCHAR(45) NOT NULL,
  `entity_type` VARCHAR(45) NOT NULL,
  `entity_id` VARCHAR(255) NOT NULL,
  `message` VARCHAR(4000) NULL,
  PRIMARY KEY (`id`))
;


-- -----------------------------------------------------
-- Table `additional_attributes`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `additional_attributes` ;

CREATE TABLE IF NOT EXISTS `additional_attributes` (
  `id` BIGINT UNSIGNED NOT NULL,
  `entity_type` VARCHAR(45) NOT NULL,
  `entity_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `value` VARCHAR(4000) NULL,
  PRIMARY KEY (`id`))
;


-- -----------------------------------------------------
-- Table `seb_client_configuration`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `seb_client_configuration` ;

CREATE TABLE IF NOT EXISTS `seb_client_configuration` (
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `institution_id` BIGINT UNSIGNED NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `date` DATETIME NOT NULL,
  `client_name` VARCHAR(4000) NOT NULL,
  `client_secret` VARCHAR(4000) NOT NULL,
  `encrypt_secret` VARCHAR(255) NULL,
  `active` INT(1) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX `sebClientCredentialsInstitutionRef_idx` (`institution_id` ASC),
  CONSTRAINT `sebClientConfigInstitutionRef`
    FOREIGN KEY (`institution_id`)
    REFERENCES `institution` (`id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
;


-- -----------------------------------------------------
-- Table `webservice_server_info`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `webservice_server_info` ;

CREATE TABLE IF NOT EXISTS `webservice_server_info` (
  `id` BIGINT UNSIGNED NOT NULL,
  `uuid` VARCHAR(255) NOT NULL,
  `service_address` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`))
;


