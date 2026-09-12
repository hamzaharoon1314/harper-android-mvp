#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum EngineError {
    #[error("Parse error")]
    ParseError,
    #[error("Internal engine error: {0}")]
    InternalError(String),
}
