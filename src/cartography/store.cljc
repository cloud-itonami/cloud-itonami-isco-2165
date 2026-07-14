(ns cartography.store
  "CartographyStore — in-memory append-only ledger and record storage for
  the ISCO-08 2165 cartographer and surveyor support actor. Handles site
  registration, proposal records, and immutable audit trail.")

(defprotocol Store
  (site [this site-id]
    "Retrieve a site record by site-id, or nil if not registered.")

  (register-site! [this site-record]
    "Register a site for field survey operations. Idempotent for testing.")

  (commit-record! [this record]
    "Append a proposal record (after governance approval). Idempotent for
    testing; in production, wired to a Durable Store or blockchain.")

  (append-ledger! [this entry]
    "Append an audit ledger entry (govenance verdict, hold, commit disposition).
    Immutable across all runs.")

  (records-of [this site-id]
    "Retrieve all records for a given site.")

  (ledger [this]
    "Retrieve the full audit ledger."))

(defn mem-store
  "Create an in-memory store for testing. Returns a Store instance."
  []
  (let [_sites (atom {})
        _ledger (atom [])
        _records (atom [])]
    (reify Store
      (site [this site-id]
        (get @_sites site-id))

      (register-site! [this site-record]
        (swap! _sites assoc (:site-id site-record) site-record))

      (commit-record! [this record]
        (swap! _records conj record))

      (append-ledger! [this entry]
        (swap! _ledger conj entry))

      (records-of [this site-id]
        (filter #(= (:site-id %) site-id) @_records))

      (ledger [this]
        @_ledger))))
