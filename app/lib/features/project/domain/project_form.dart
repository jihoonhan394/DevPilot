import 'package:devpilot_app/core/api/api_enums.dart';
import 'package:devpilot_app/core/validation/input_rules.dart';
import 'package:devpilot_app/features/project/data/side_project_models.dart';
import 'package:flutter/foundation.dart';

/// Field problems of the project sheet (docs/02 §3.2 side project rows).
enum ProjectFieldViolation {
  nameRequired,
  nameTooLong,
  descriptionTooLong,
  repoUrlInvalid,
  repoUrlTooLong,
  stackTooLong,
}

/// Values of `ProjectEditSheet` (docs/02 SCR-PROJECTS). [original] is null when adding.
@immutable
final class ProjectFormValues {
  const ProjectFormValues({
    required this.original,
    required this.name,
    required this.description,
    required this.repoUrl,
    required this.stack,
    required this.status,
  });

  /// A new project, optionally prefilled ("주문 시스템으로 시작").
  factory ProjectFormValues.create({String name = '', String description = ''}) =>
      ProjectFormValues(
        original: null,
        name: name,
        description: description,
        repoUrl: '',
        stack: '',
        status: SideProjectStatus.active,
      );

  factory ProjectFormValues.edit(SideProjectView project) => ProjectFormValues(
    original: project,
    name: project.name,
    description: project.description ?? '',
    repoUrl: project.repoUrl ?? '',
    stack: project.stack ?? '',
    status: project.status,
  );

  final SideProjectView? original;
  final String name;
  final String description;
  final String repoUrl;
  final String stack;
  final SideProjectStatus status;

  bool get isCreate => original == null;

  Set<ProjectFieldViolation> get violations => {
    if (name.trim().isEmpty) ProjectFieldViolation.nameRequired,
    if (name.length > InputRules.projectNameMaxLength) ProjectFieldViolation.nameTooLong,
    if (description.length > InputRules.projectDescriptionMaxLength)
      ProjectFieldViolation.descriptionTooLong,
    if (repoUrl.isNotEmpty && !InputRules.isHttpUrl(repoUrl)) ProjectFieldViolation.repoUrlInvalid,
    if (repoUrl.length > InputRules.projectRepoUrlMaxLength) ProjectFieldViolation.repoUrlTooLong,
    if (stack.length > InputRules.projectStackMaxLength) ProjectFieldViolation.stackTooLong,
  };

  /// "저장" needs valid input and a change (docs/02 SCR-PROJECTS "행동·검증").
  bool get canSave => violations.isEmpty && hasChanges;

  bool get hasChanges {
    final project = original;
    if (project == null) {
      return name.isNotEmpty || description.isNotEmpty || repoUrl.isNotEmpty || stack.isNotEmpty;
    }
    return toPatchRequest(project).toJson().length > 1;
  }

  /// `POST /side-projects`: empty optional fields are sent as null.
  SideProjectCreateRequest toCreateRequest() => SideProjectCreateRequest(
    name: name,
    description: _nullIfEmpty(description),
    repoUrl: _nullIfEmpty(repoUrl.trim()),
    stack: _nullIfEmpty(stack),
  );

  /// `PATCH /side-projects/{id}`: only changed fields; a cleared optional field is sent as `""`
  /// (the server stores null). The name cannot be cleared (docs/05 §19.5).
  SideProjectPatchRequest toPatchRequest(SideProjectView project) => SideProjectPatchRequest(
    name: name == project.name ? null : name,
    description: _changed(project.description, description),
    repoUrl: _changed(project.repoUrl, repoUrl.trim()),
    stack: _changed(project.stack, stack),
    status: status == project.status ? null : status,
    version: project.version,
  );

  ProjectFormValues copyWith({
    String? name,
    String? description,
    String? repoUrl,
    String? stack,
    SideProjectStatus? status,
  }) => ProjectFormValues(
    original: original,
    name: name ?? this.name,
    description: description ?? this.description,
    repoUrl: repoUrl ?? this.repoUrl,
    stack: stack ?? this.stack,
    status: status ?? this.status,
  );

  static String? _nullIfEmpty(String value) => value.isEmpty ? null : value;

  /// The value to PATCH, or null when unchanged. Empty text clears the field.
  static String? _changed(String? saved, String edited) => (saved ?? '') == edited ? null : edited;
}

/// Link text of a repository URL: without the scheme (docs/02 SCR-PROJECTS "repoUrl").
String repoLinkText(String url) => url.replaceFirst(RegExp('^https?://'), '');

/// Only https links open; http stays plain text (docs/09 §12, docs/07 §9.5).
bool isOpenableRepoUrl(String url) => Uri.tryParse(url)?.scheme == 'https';
