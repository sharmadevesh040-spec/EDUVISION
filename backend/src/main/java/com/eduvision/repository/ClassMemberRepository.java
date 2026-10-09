package com.eduvision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eduvision.domain.ClassMember;
import com.eduvision.domain.EduClass;
import com.eduvision.domain.User;

public interface ClassMemberRepository extends JpaRepository<ClassMember, Long> {

    List<ClassMember> findByEduClassId(Long eduClassId);

    List<ClassMember> findByEduClass(EduClass eduClass);

    List<ClassMember> findByStudentId(Long studentId);

    List<ClassMember> findByStudent(User student);

    java.util.Optional<ClassMember> findByEduClassAndStudent(EduClass eduClass, User student);

    boolean existsByEduClassIdAndStudentId(Long eduClassId, Long studentId);

    boolean existsByEduClassAndStudent(EduClass eduClass, User student);

    long countByEduClassId(Long eduClassId);
}