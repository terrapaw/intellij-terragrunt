locals {
  aws_region   = "us-east-1"
  account_id   = "123456789012"
  project_name = "my-project"
}

inputs = {
  default_tags = {
    Project   = "my-project"
    ManagedBy = "terragrunt"
  }
}
