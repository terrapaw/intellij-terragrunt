# Root Terragrunt configuration
# This file is included by all child terragrunt.hcl files

locals {
  aws_region   = "us-east-1"
  project_name = "my-infra"
  account_id   = get_aws_account_id()
}

remote_state {
  backend = "s3"
  config = {
    bucket         = "${local.project_name}-terraform-state"
    key            = "${path_relative_to_include()}/terraform.tfstate"
    region         = local.aws_region
    encrypt        = true
    dynamodb_table = "${local.project_name}-lock-table"
  }
}

generate "provider" {
  path      = "provider.tf"
  if_exists = "overwrite_terragrunt"
  contents  = <<EOF
provider "aws" {
  region = "${local.aws_region}"

  default_tags {
    tags = {
      ManagedBy = "terragrunt"
      Project   = "${local.project_name}"
    }
  }
}
EOF
}
