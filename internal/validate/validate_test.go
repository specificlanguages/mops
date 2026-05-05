package validate

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestRunRejectsMalformedTrailingXMLAfterModelRoot(t *testing.T) {
	root := t.TempDir()
	modelPath := filepath.Join(root, "broken.mps")
	if err := os.WriteFile(modelPath, []byte(`<model><persistence version="9"/></model><broken`), 0o644); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}

	report := Run([]string{modelPath})

	if report.Status != "failed" {
		t.Fatalf("status = %q, want failed", report.Status)
	}
	if len(report.Findings) != 1 {
		t.Fatalf("findings = %#v, want one finding", report.Findings)
	}
	finding := report.Findings[0]
	if finding.Code != "malformed-xml" {
		t.Fatalf("code = %q, want malformed-xml", finding.Code)
	}
	if finding.Location == nil || finding.Location.Line == 0 || finding.Location.Column == 0 {
		t.Fatalf("location = %#v, want line and column", finding.Location)
	}
}

func TestRunReportsInvalidRegularNodeID(t *testing.T) {
	tests := []struct {
		name string
		id   string
	}{
		{name: "leading zero regular ID", id: "0001"},
		{name: "custom ID family", id: "custom-id"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			modelPath := writeModel(t, `<model ref="r:sample">
  <persistence version="9" />
  <node concept="1" id="`+tt.id+`" />
</model>`)

			report := Run([]string{modelPath})

			assertFinding(t, report, "error", "invalid-node-id", filepath.ToSlash(modelPath))
		})
	}
}

func TestRunAcceptsSupportedNodeIDs(t *testing.T) {
	modelPath := writeModel(t, `<model ref="r:sample">
  <persistence version="9" />
  <node concept="1" id="0" />
  <node concept="1" id="1V" />
  <node concept="1" id="~" />
  <node concept="1" id="~foreign" />
</model>`)

	report := Run([]string{modelPath})

	if report.HasErrors() {
		t.Fatalf("HasErrors() = true, findings = %#v", report.Findings)
	}
}

func TestRunReportsForbiddenDynamicNodeIDMarker(t *testing.T) {
	modelPath := writeModel(t, `<model ref="r:sample">
  <persistence version="9" />
  <node concept="1" id="^" />
</model>`)

	report := Run([]string{modelPath})

	assertFinding(t, report, "error", "forbidden-dynamic-node-id", filepath.ToSlash(modelPath))
}

func TestRunReportsDuplicateNodeIDsInStandaloneTarget(t *testing.T) {
	modelPath := writeModel(t, `<model ref="r:sample">
  <persistence version="9" />
  <node concept="1" id="1">
    <node concept="2" id="2" role="3" />
  </node>
  <node concept="1" id="2" />
</model>`)

	report := Run([]string{modelPath})

	assertFinding(t, report, "error", "duplicate-node-id", filepath.ToSlash(modelPath))
}

func TestRunReportsDuplicateNodeIDsAcrossDirectRootFilesInFilePerRootFolder(t *testing.T) {
	root := t.TempDir()
	modelFolder := filepath.Join(root, "models", "sample")
	secondRoot := filepath.Join(modelFolder, "second.mpsr")
	writeFile(t, filepath.Join(modelFolder, ".model"), `<model ref="r:sample"><persistence version="9" /></model>`)
	writeFile(t, filepath.Join(modelFolder, "first.mpsr"), `<node concept="1" id="1" />`)
	writeFile(t, secondRoot, `<node concept="1" id="1" />`)

	report := Run([]string{modelFolder})

	assertFinding(t, report, "error", "duplicate-node-id", filepath.ToSlash(modelFolder))
	finding := findFinding(t, report, "error", "duplicate-node-id", filepath.ToSlash(modelFolder))
	if finding.File != filepath.ToSlash(secondRoot) {
		t.Fatalf("file = %q, want %q", finding.File, filepath.ToSlash(secondRoot))
	}
}

func TestRunReportsFileForInvalidRootFileFindingInFilePerRootFolder(t *testing.T) {
	root := t.TempDir()
	modelFolder := filepath.Join(root, "models", "sample")
	brokenRoot := filepath.Join(modelFolder, "broken.mpsr")
	writeFile(t, filepath.Join(modelFolder, ".model"), `<model ref="r:sample"><persistence version="9" /></model>`)
	writeFile(t, brokenRoot, `<node concept="1" id="not-supported" />`)

	report := Run([]string{modelFolder})

	finding := findFinding(t, report, "error", "invalid-node-id", filepath.ToSlash(modelFolder))
	if finding.File != filepath.ToSlash(brokenRoot) {
		t.Fatalf("file = %q, want %q", finding.File, filepath.ToSlash(brokenRoot))
	}
}

func TestRunAcceptsFilePerRootFolderWithDirectRootFiles(t *testing.T) {
	root := t.TempDir()
	modelFolder := filepath.Join(root, "models", "sample")
	writeFile(t, filepath.Join(modelFolder, ".model"), `<model ref="r:sample"><persistence version="9" /></model>`)
	writeFile(t, filepath.Join(modelFolder, "first.mpsr"), `<node concept="1" id="1" />`)
	writeFile(t, filepath.Join(modelFolder, "second.mpsr"), `<node concept="1" id="2" />`)

	report := Run([]string{modelFolder})

	if report.HasErrors() {
		t.Fatalf("HasErrors() = true, findings = %#v", report.Findings)
	}
}

func TestRunIgnoresNestedRootFilesInFilePerRootFolder(t *testing.T) {
	root := t.TempDir()
	modelFolder := filepath.Join(root, "models", "sample")
	writeFile(t, filepath.Join(modelFolder, ".model"), `<model ref="r:sample"><persistence version="9" /></model>`)
	writeFile(t, filepath.Join(modelFolder, "root.mpsr"), `<node concept="1" id="1" />`)
	writeFile(t, filepath.Join(modelFolder, "nested", "ignored.mpsr"), `<node concept="1" id="1" />`)

	report := Run([]string{modelFolder})

	if report.HasErrors() {
		t.Fatalf("HasErrors() = true, findings = %#v", report.Findings)
	}
}

func TestRunTreatsStandaloneRootFileAsIncompleteValidation(t *testing.T) {
	rootPath := filepath.Join(t.TempDir(), "root.mpsr")
	writeFile(t, rootPath, `<node concept="1" id="1" />`)

	report := Run([]string{rootPath})

	if report.HasErrors() {
		t.Fatalf("HasErrors() = true, findings = %#v", report.Findings)
	}
	if report.Status != "ok" {
		t.Fatalf("status = %q, want ok", report.Status)
	}
	finding := findFinding(t, report, "info", "incomplete-validation", filepath.ToSlash(rootPath))
	assertContains(t, finding.Message, "for complete structural validation")
	assertContains(t, finding.Message, "mops validate "+filepath.ToSlash(filepath.Dir(rootPath)))
}

func writeModel(t *testing.T, content string) string {
	t.Helper()
	path := filepath.Join(t.TempDir(), "sample.mps")
	writeFile(t, path, content)
	return path
}

func writeFile(t *testing.T, path, content string) {
	t.Helper()
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatalf("MkdirAll() error = %v", err)
	}
	if err := os.WriteFile(path, []byte(content), 0o644); err != nil {
		t.Fatalf("WriteFile() error = %v", err)
	}
}

func assertFinding(t *testing.T, report Report, severity, code, target string) {
	t.Helper()
	_ = findFinding(t, report, severity, code, target)
}

func findFinding(t *testing.T, report Report, severity, code, target string) Finding {
	t.Helper()
	for _, finding := range report.Findings {
		if finding.Severity == severity && finding.Code == code && finding.Target == target {
			return finding
		}
	}
	t.Fatalf("finding (%s, %s, %s) not found in %#v", severity, code, target, report.Findings)
	return Finding{}
}

func assertContains(t *testing.T, s, substr string) {
	t.Helper()
	if !strings.Contains(s, substr) {
		t.Fatalf("%q does not contain %q", s, substr)
	}
}
