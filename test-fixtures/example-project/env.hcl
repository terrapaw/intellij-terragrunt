# Environment-level configuration
locals {
  environment = "dev"
  vpc_cidr    = "10.0.0.0/16"
}

inputs = {
  default_tags = {
    Environment = "dev"
    ManagedBy   = "terragrunt"
  }
  log_level = "info"
}
