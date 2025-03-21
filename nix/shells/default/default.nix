{
  # Snowfall Lib provides a customized `lib` instance with access to your flake's library
  # as well as the libraries available from your flake's inputs.
  lib,
  # You also have access to your flake's inputs.
  inputs,

  # The namespace used for your flake, defaulting to "internal" if not set.
  namespace,

  # All other arguments come from NixPkgs. You can use `pkgs` to pull shells or helpers
  # programmatically or you may add the named attributes as arguments here.
  pkgs,
  ...
}:
let
  my = {
    polylith = pkgs.polylith.override { jdk = pkgs.jdk23; };
  };
in

inputs.devenv.lib.mkShell {
  inherit inputs pkgs;
  modules = [
    (
      { pkgs, config, ... }:
      {
        git-hooks.hooks = {
          nixfmt-rfc-style.enable = true;
          cljfmt.enable = true;
        };

        languages = {
          clojure.enable = true;
          java = {
            enable = true;
            jdk.package = pkgs.jdk23;
          };
        };

        packages = [
          my.polylith

          pkgs.clj-kondo
          pkgs.cljfmt
          pkgs.clojure-lsp
          pkgs.httpie
          pkgs.jdk23
          pkgs.unzip
        ];
      }
    )
  ];
}
