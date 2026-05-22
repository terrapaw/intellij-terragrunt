# Common configuration loaded via read_terragrunt_config()
locals {
  org_name    = "acme"
  team        = "platform"
  cost_center = "engineering"
}

inputs = {
  default_tags = {
    Organization = "acme"
    Team         = "platform"
    ManagedBy    = "terragrunt"
  }
  notification_email = "platform-team@acme.com"
  alert_slack_channel = "#infra-alerts"
}
