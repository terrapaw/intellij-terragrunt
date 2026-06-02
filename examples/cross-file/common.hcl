locals {
  org_name = "terrapaw"
  team     = "platform"

  # Try: local.common.locals.network.vpc_cidr in the child file
  network = {
    "vpc_cidr" = "10.0.0.0/16"
    az_count = 3
    subnets  = {
      "public"  = "10.0.1.0/24"
      private = "10.0.2.0/24"
    }
  }
}

inputs = {
  notification_email  = "alerts@example.com"
  alert_slack_channel = "#infra-alerts"
}
