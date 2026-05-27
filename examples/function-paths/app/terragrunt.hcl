# Function-aware path resolution in includes and read_terragrunt_config
#
# Supported functions:
#   get_terragrunt_dir(), get_parent_terragrunt_dir(), get_root_terragrunt_dir()
#   get_repo_root(), get_path_to_repo_root(), get_path_from_repo_root()
#   find_in_parent_folders("X"), dirname(X), basename(X)

# get_parent_terragrunt_dir() walks up to find parent dir with root.hcl/terragrunt.hcl
include "root" {
  path = "${get_parent_terragrunt_dir()}/root.hcl"
}

locals {
  # get_terragrunt_dir() returns the directory of THIS file
  # dirname(find_in_parent_folders("root.hcl")) returns the dir containing root.hcl
  shared = read_terragrunt_config("${dirname(find_in_parent_folders(\"root.hcl\"))}/shared.hcl")

  # Try: local.shared.locals. → autocomplete shows shared_vpc_cidr, shared_env
  cidr = local.shared.locals.shared_vpc_cidr
}

inputs = {
  env = local.shared.locals.shared_env
}
