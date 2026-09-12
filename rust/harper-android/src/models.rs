#[derive(uniffi::Record)]
pub struct LintResult {
    pub start_utf16: u32,
    pub end_utf16: u32,
    pub message: String,
    pub suggestions: Vec<String>,
}
