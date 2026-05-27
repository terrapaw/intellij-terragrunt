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
  # dirname(find_in_parent_folders("root.hcl")) returns the dir containing root.hcl
  shared = read_terragrunt_config("${dirname(find_in_parent_folders(\"root.hcl\"))}/shared.hcl")

  # get_terragrunt_dir() returns the directory of THIS file
  local_config = read_terragrunt_config("${get_terragrunt_dir()}/local.hcl")

  # get_root_terragrunt_dir() finds the topmost dir with root.hcl
  root_config = read_terragrunt_config("${get_root_terragrunt_dir()}/root.hcl")

  # get_repo_root() finds the .git directory
  repo_config = read_terragrunt_config("${get_repo_root()}/shared.hcl")

  # get_path_to_repo_root() returns relative path to git root (e.g. "../..")
  # get_path_from_repo_root() returns path from git root (e.g. "app")
  # basename(get_terragrunt_dir()) returns just the directory name (e.g. "app")

  # Try: local.shared.locals. → autocomplete shows shared_vpc_cidr, shared_env
  cidr = local.shared.locals.shared_vpc_cidr
}

inputs = {
  env = local.shared.locals.shared_env
}
