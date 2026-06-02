# Cross-file resolution: includes, aliases, read_terragrunt_config

include "root" {
  path   = find_in_parent_folders("root.hcl")
  expose = true
}

locals {
  # read_terragrunt_config loads another file's config
  common = read_terragrunt_config(find_in_parent_folders("common.hcl"))

  # Alias pattern: local.root_config.X resolves through include
  root_config = include.root.locals

  # Try: local.root_config. → autocomplete shows aws_region, account_id, project_name
  region = local.root_config.aws_region
}

inputs = {
  # Try: Ctrl+B on project_name → jumps to root.hcl
  project = include.root.locals.project_name

  # Try: include.root.inputs. → autocomplete shows default_tags
  tags = include.root.inputs.default_tags

  # Try: local.common.locals. → autocomplete shows org_name, team, network
  org = local.common.locals.org_name

  # Try: local.common.inputs. → autocomplete shows notification_email, alert_slack_channel
  alerts = local.common.inputs.notification_email

  # Cross-file nested object navigation:
  # Try: local.common.locals.network. → suggests vpc_cidr, az_count, subnets
  # Try: local.common.locals.network.subnets. → suggests public, private
  # Try: Ctrl+B on vpc_cidr → jumps to common.hcl
  vpc_cidr = local.common.locals.network.vpc_cidr
  public   = local.common.locals.network.subnets.public
}
