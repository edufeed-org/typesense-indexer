# Typesense Indexer

Listens for AMB events of a certain relay and indexes them to a Typesearch instance.

## Run

- Adjust config in `resources/config.edn`
- Run with `clj -M -m typesense-indexer.system`

or do it with docker:

- `docker compose build`
- `docker compose up`

## TODO

- [ ] Use multiple relays
- [ ] Write Docs

