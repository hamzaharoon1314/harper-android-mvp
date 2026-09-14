#[derive(uniffi::Enum, Clone, Copy, PartialEq, Eq)]
pub enum HarperDialect {
    American,
    Canadian,
    Australian,
    British,
    Indian,
}

#[derive(uniffi::Enum, Clone, Copy, PartialEq, Eq)]
pub enum DocumentMode {
    PlainEnglish,
    Markdown,
}

#[derive(uniffi::Record)]
pub struct HarperConfig {
    pub dialect: HarperDialect,
    pub document_mode: DocumentMode,
    pub disabled_rules: Vec<String>,
    pub user_dictionary: Vec<String>,
}

#[derive(uniffi::Record)]
pub struct HarperLint {
    pub issue_id: String,
    pub start_utf16: u32,
    pub end_utf16: u32,
    pub message: String,
    pub rule_id: Option<String>,
    pub suggestions: Vec<HarperSuggestion>,
}

#[derive(uniffi::Record)]
pub struct HarperSuggestion {
    pub suggestion_id: String,
    pub display_text: String,
    pub operation: EditOperation,
}

#[derive(uniffi::Enum)]
pub enum EditOperation {
    ReplaceWith { replacement: String },
    InsertAfter { insertion: String },
    Remove,
}

#[derive(uniffi::Record)]
pub struct AnalysisMetadata {
    pub version: String,
    pub execution_time_ms: u32,
}

impl From<HarperDialect> for harper_core::Dialect {
    fn from(val: HarperDialect) -> Self {
        match val {
            HarperDialect::American => harper_core::Dialect::American,
            HarperDialect::Canadian => harper_core::Dialect::Canadian,
            HarperDialect::Australian => harper_core::Dialect::Australian,
            HarperDialect::British => harper_core::Dialect::British,
            HarperDialect::Indian => harper_core::Dialect::Indian,
        }
    }
}
