-- Citation records are identified by document/chunk and displayed by file name.
-- Page numbers remain parser metadata on document chunks, but are not part of
-- the user-facing citation persistence contract.
ALTER TABLE kb_chat_message_citation
    DROP COLUMN IF EXISTS page_no;
