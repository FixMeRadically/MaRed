param([string]$Root)
$ErrorActionPreference='Stop'
$src=Join-Path $Root 'qa\02_Семейное_дерево.docx'
Copy-Item -LiteralPath (Join-Path $Root 'input\ФИНАЛ\02_Семейное_дерево.docx') -Destination $src -Force
$dst=Join-Path $Root 'qa\test-linked.docx'
Copy-Item -LiteralPath (Join-Path $Root 'input\ФИНАЛ\02_Отчёт.docx') -Destination $dst -Force
$w=New-Object -ComObject Word.Application
$w.Visible=$false
$w.DisplayAlerts=0
$a=$null;$b=$null
try{
 $a=$w.Documents.Open($src,$false,$false)
 $r=$a.Content.Duplicate
 $r.End=$r.End-1
 $a.Bookmarks.Add('ReportTree',$r) | Out-Null
 $a.Save()
 $b=$w.Documents.Open($dst,$false,$false)
 $picture=$b.InlineShapes.Item(2)
 $height=$picture.Height
 $target=$picture.Range.Duplicate
 $picture.Delete()
 $field=$b.Fields.Add($target,56,('Word.Document.12 "'+$src.Replace('\','\\')+'" "ReportTree" \a \p'),$false)
 $field.Update() | Out-Null
 Write-Output ('Fields: '+$b.Fields.Count+' Pictures: '+$b.InlineShapes.Count)
 foreach($f in $b.Fields){Write-Output ('CODE: '+$f.Code.Text)}
 foreach($p in $b.InlineShapes){Write-Output ('Shape: '+$p.Type+' '+$p.Width+'x'+$p.Height)}
 $b.Save()
 $b.ExportAsFixedFormat((Join-Path $Root 'qa\test-linked.pdf'),17)
}finally{if($b){$b.Close(0)};if($a){$a.Close(0)};$w.Quit()}
